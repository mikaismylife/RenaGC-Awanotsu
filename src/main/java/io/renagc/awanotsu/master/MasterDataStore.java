package io.renagc.awanotsu.master;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.RijndaelEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Master-data store: knows where encrypted master tables live and what version
 * they are, and can decrypt one for verification.
 *
 * <p><b>Encrypted .bin format:</b>
 * {@code [32B salt][32B IV][ Rijndael-256-CBC/PKCS7(key)( gzip( JSON {"_allData":[...]} ) ) ]}.
 * Key = the locally configured 32-byte hex value; IV is per-file (file bytes
 * 32..63); salt (bytes 0..31) is informational. Rijndael uses a 256-bit block
 * (not standard AES), hence BouncyCastle's {@link RijndaelEngine}.
 */
public final class MasterDataStore {

    private static final Logger log = LoggerFactory.getLogger(MasterDataStore.class);

    /** File holding the master version string (MasterDataManager.MasterHashFilename). */
    public static final String VERSION_FILENAME = "MasterDataSystemVersion.txt";
    private static final int RIJNDAEL_BLOCK_BITS = 256;
    private static final int HEADER_BYTES = 64; // 32B salt + 32B IV

    private final Path dir;         // null when no master dir configured
    private final Path overrideDir; // null when no overrides; shadows same-named tables in dir
    private final byte[] key;       // 32-byte decrypt key
    private final String version;   // resolved version string (real file, else fallback)
    private final boolean available;

    /** Cache of decrypted+parsed {@code _allData} arrays, keyed by logical table name. */
    private final Map<String, JsonArray> tableCache = new ConcurrentHashMap<>();

    private MasterDataStore(Path dir, Path overrideDir, byte[] key, String version, boolean available) {
        this.dir = dir;
        this.overrideDir = overrideDir;
        this.key = key;
        this.version = version;
        this.available = available;
    }

    /** Back-compat: open with no overrides. */
    public static MasterDataStore open(String dirPath, String keyHex, String fallbackVersion) {
        return open(dirPath, null, keyHex, fallbackVersion);
    }

    /**
     * Open a store over {@code dirPath}, with {@code overrideDirPath} shadowing same-named
     * {@code *.bin} tables (e.g. the masterdata-unlocked {@code MasterLiveMusic.bin}). If
     * the main dir is null/missing or has no version file, returns an unavailable store
     * carrying {@code fallbackVersion} (the server still boots; Version RPC returns it).
     */
    public static MasterDataStore open(String dirPath, String overrideDirPath,
                                       String keyHex, String fallbackVersion) {
        byte[] key = hexToBytes(keyHex);
        Path override = null;
        if (overrideDirPath != null && !overrideDirPath.isBlank()) {
            Path o = Path.of(overrideDirPath);
            if (Files.isDirectory(o)) {
                override = o;
            } else {
                log.warn("masterdata.overrideDir not found ({}) - ignoring.", o.toAbsolutePath());
            }
        }
        if (dirPath == null || dirPath.isBlank()) {
            log.warn("masterdata.dir unset - serving fallback version \"{}\", no CDN.", fallbackVersion);
            return new MasterDataStore(null, override, key, fallbackVersion, false);
        }
        Path dir = Path.of(dirPath);
        if (!Files.isDirectory(dir)) {
            log.warn("masterdata.dir not found ({}) - fallback version \"{}\", no CDN.",
                    dir.toAbsolutePath(), fallbackVersion);
            return new MasterDataStore(null, override, key, fallbackVersion, false);
        }
        Path versionFile = dir.resolve(VERSION_FILENAME);
        String version = fallbackVersion;
        boolean available = false;
        if (Files.isRegularFile(versionFile)) {
            try {
                version = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
                available = true;
            } catch (IOException e) {
                log.warn("Could not read {} ({}); using fallback version.", versionFile, e.toString());
            }
        } else {
            log.warn("{} missing in {}; using fallback version.", VERSION_FILENAME, dir.toAbsolutePath());
        }
        long tables = listTables(dir).size();
        long overrides = override == null ? 0 : listTables(override).size();
        log.info("MasterDataStore: dir={} override={} version={} tables={} overrides={} available={}",
                dir.toAbsolutePath(), override == null ? "(none)" : override.toAbsolutePath(),
                version, tables, overrides, available);
        return new MasterDataStore(dir, override, key, version, available);
    }

    /** The version string the gRPC Version RPC should return. Never null. */
    public String version() {
        return version;
    }

    /** True when a real master dir + version file were found. */
    public boolean available() {
        return available;
    }

    /** Master dir (may be null when unavailable). */
    public Path dir() {
        return dir;
    }

    /** Encrypted-table filenames present (e.g. {@code MasterBand.bin}), version file excluded. */
    public List<String> tableFiles() {
        return dir == null ? List.of() : listTables(dir);
    }

    private static List<String> listTables(Path dir) {
        List<String> names = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir, "*.bin")) {
            for (Path p : stream) {
                names.add(p.getFileName().toString());
            }
        } catch (IOException e) {
            log.warn("Could not list master tables in {}: {}", dir, e.toString());
        }
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Raw encrypted bytes of one table file (the exact CDN blob), or null if absent. An
     * override file (same name in {@code overrideDir}) wins over the original.
     */
    public byte[] readEncrypted(String fileName) throws IOException {
        if (overrideDir != null) {
            Path op = overrideDir.resolve(fileName).normalize();
            if (op.startsWith(overrideDir) && Files.isRegularFile(op)) {
                return Files.readAllBytes(op);
            }
        }
        if (dir == null) return null;
        Path p = dir.resolve(fileName).normalize();
        // Contain to the master dir (defend the HTTP handler against path traversal).
        if (!p.startsWith(dir) || !Files.isRegularFile(p)) {
            return null;
        }
        return Files.readAllBytes(p);
    }

    /**
     * Decrypt a single encrypted table blob to its plaintext JSON, exactly as the
     * client does: split [salt][IV][cipher], Rijndael-256-CBC/PKCS7 decrypt with the
     * key + per-file IV, then gunzip. Returns the UTF-8 JSON string.
     */
    public String decryptTable(byte[] blob) throws IOException {
        if (blob == null || blob.length <= HEADER_BYTES) {
            throw new IOException("blob too short to be an encrypted master table");
        }
        byte[] iv = new byte[32];
        System.arraycopy(blob, 32, iv, 0, 32);
        int cipherLen = blob.length - HEADER_BYTES;

        byte[] gz = rijndael256DecryptCbcPkcs7(blob, HEADER_BYTES, cipherLen, key, iv);
        return new String(gunzip(gz), StandardCharsets.UTF_8);
    }

    /** Convenience: read + decrypt a named table to its JSON string. */
    public String decryptTableFile(String fileName) throws IOException {
        byte[] blob = readEncrypted(fileName);
        if (blob == null) {
            throw new IOException("no such master table: " + fileName);
        }
        return decryptTable(blob);
    }

    // --- typed table access (for the login/home/play flow) -------------------

    /**
     * Decrypted + parsed {@code _allData} rows of one logical master table
     * (e.g. {@code "MasterLiveMusic"} or {@code "MasterLiveMusic.bin"}). Cached.
     * Returns an empty array if the store is unavailable or the table is missing.
     * Callers must tolerate emptiness.
     */
    public JsonArray table(String tableName) {
        String key = tableName.endsWith(".bin")
                ? tableName.substring(0, tableName.length() - 4)
                : tableName;
        return tableCache.computeIfAbsent(key, this::loadTable);
    }

    private JsonArray loadTable(String tableName) {
        if (dir == null) {
            return new JsonArray();
        }
        try {
            String json = decryptTableFile(tableName + ".bin");
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement all = root.get("_allData");
            if (all != null && all.isJsonArray()) {
                JsonArray arr = all.getAsJsonArray();
                log.debug("Loaded master table {} ({} rows).", tableName, arr.size());
                return arr;
            }
            log.warn("Master table {} has no _allData array.", tableName);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not load master table {}: {}", tableName, e.toString());
        }
        return new JsonArray();
    }

    /** First row in {@code tableName} whose {@code idField} equals {@code id}, or null. */
    public JsonObject findRow(String tableName, String idField, long id) {
        for (JsonElement e : table(tableName)) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement v = o.get(idField);
            if (v != null && v.isJsonPrimitive() && v.getAsLong() == id) {
                return o;
            }
        }
        return null;
    }

    // --- crypto primitives ---------------------------------------------------

    private static byte[] rijndael256DecryptCbcPkcs7(byte[] in, int off, int len,
                                                      byte[] key, byte[] iv) throws IOException {
        RijndaelEngine engine = new RijndaelEngine(RIJNDAEL_BLOCK_BITS);
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                CBCBlockCipher.newInstance(engine), new PKCS7Padding());
        cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] out = new byte[cipher.getOutputSize(len)];
        try {
            int p = cipher.processBytes(in, off, len, out, 0);
            p += cipher.doFinal(out, p);
            if (p == out.length) {
                return out;
            }
            byte[] trimmed = new byte[p];
            System.arraycopy(out, 0, trimmed, 0, p);
            return trimmed;
        } catch (Exception e) {
            throw new IOException("Rijndael-256 decrypt failed: " + e, e);
        }
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isBlank()) {
            return new byte[32];
        }
        if (hex.length() != 64) {
            throw new IllegalArgumentException("masterdata.keyHex must be 64 hex characters");
        }
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
