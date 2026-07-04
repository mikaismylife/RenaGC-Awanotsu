package io.renagc.awanotsu.client;

import io.renagc.awanotsu.Config;
import io.renagc.awanotsu.master.MasterDataStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Master-data smoke test (no running server needed).
 *
 * <p>Loads the configured master DB and asserts:
 * <ol>
 *   <li>a non-empty version string was read from MasterDataSystemVersion.txt;</li>
 *   <li>the encrypted tables are present;</li>
 *   <li>a sample table round-trips: Rijndael-256-CBC/PKCS7 decrypt + gunzip yields
 *       valid JSON shaped {@code {"_allData":[ ... ]}} with at least one row.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   gradlew masterdataSmoke
 *   gradlew masterdataSmoke --args="resources/masterdata <64-char-key-hex>"
 * </pre>
 */
public final class MasterdataSmoke {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.load(Path.of("config.json"));
        String dir = args.length > 0 ? args[0] : cfg.masterdata.dir;
        String keyHex = args.length > 1 ? args[1] : cfg.masterdata.keyHex;

        System.out.println("== RenaGC master-data smoke test ==");
        System.out.println("master dir: " + dir);

        MasterDataStore store = MasterDataStore.open(dir, keyHex, cfg.masterVersion);

        String version = store.version();
        boolean versionOk = store.available() && version != null && !version.isBlank();
        System.out.println("[1] version (MasterDataSystemVersion.txt) -> \"" + version + "\""
                + (versionOk ? "" : "  (FALLBACK / not available!)"));

        List<String> tables = store.tableFiles();
        boolean tablesOk = tables.size() >= 100;
        System.out.println("[2] encrypted tables present -> " + tables.size()
                + (tables.isEmpty() ? "" : "  (e.g. " + tables.get(0) + ")"));

        boolean roundTripOk = false;
        String sample = pickSample(tables);
        if (sample != null) {
            byte[] blob = store.readEncrypted(sample);
            String json = store.decryptTable(blob);
            int rows = countAllDataRows(json);
            boolean shaped = json.contains("\"_allData\"") && rows >= 1;
            roundTripOk = shaped;
            String preview = json.length() > 120 ? json.substring(0, 120).replace("\n", " ") + "..." : json;
            System.out.println("[3] decrypt(" + sample + ") -> " + json.length()
                    + " bytes JSON, _allData rows=" + rows);
            System.out.println("    preview: " + preview);
        } else {
            System.out.println("[3] no .bin table to decrypt!");
        }

        boolean ok = versionOk && tablesOk && roundTripOk;
        System.out.println("== MASTERDATA SMOKE " + (ok ? "PASS" : "FAIL") + " ==");
        if (!ok) {
            System.exit(2);
        }
    }

    /** Prefer a small, known table; else the first available. */
    private static String pickSample(List<String> tables) {
        for (String pref : List.of("MasterBand.bin", "MasterCharacter.bin")) {
            if (tables.contains(pref)) return pref;
        }
        return tables.isEmpty() ? null : tables.get(0);
    }

    private static int countAllDataRows(String json) {
        int idx = json.indexOf("\"_allData\"");
        if (idx < 0) return 0;
        int open = json.indexOf('[', idx);
        if (open < 0) return 0;
        int depth = 0, rows = 0;
        boolean inString = false;
        boolean counted = false;
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '"' && json.charAt(i - 1) != '\\') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> {
                    if (depth == 0) {
                        rows++;
                        counted = true;
                    }
                    depth++;
                }
                case '}' -> depth--;
                case ']' -> {
                    if (depth == 0) {
                        return rows;
                    }
                }
                default -> { /* ignore */ }
            }
        }
        return counted ? rows : 0;
    }
}
