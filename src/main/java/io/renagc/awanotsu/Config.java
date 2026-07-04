package io.renagc.awanotsu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config.json POJO. Tolerant: unknown/extra keys are ignored, and a missing
 * file falls back to publishable defaults so the server can still boot.
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** gRPC listen settings. */
    public Grpc grpc = new Grpc();

    /** Mongo connection string. The main server requires MongoDB for persistence. */
    public String mongo = "mongodb://localhost:27017/renagc";

    /**
     * Master-data version string returned by app.masterdata.MasterdataService/Version.
     * Used as a fallback when the configured master-data directory is absent.
     */
    public String masterVersion = "0.0.0-renagc";

    /** Master-data serving and decode config. */
    public Masterdata masterdata = new Masterdata();

    /** External resource-bundle serving config. */
    public Asset asset = new Asset();

    /** Self-signed TLS settings. Material is generated on first use if absent. */
    public Tls tls = new Tls();

    /** Remote HTTP operator console. */
    public Console console = new Console();

    public static final class Grpc {
        /** Plaintext h2c port for local testing. */
        public int port = 20000;
        /** 0.0.0.0 binds all interfaces. */
        public String host = "0.0.0.0";
    }

    /**
     * TLS listener config. When enabled, a second gRPC listener is started on
     * {@link #grpcPort}; the plain local listener remains available.
     */
    public static final class Tls {
        /** Default off so the local h2c flow is unchanged. */
        public boolean enabled = false;
        /** TLS gRPC listen port. */
        public int grpcPort = 443;
        /** PKCS12 keystore path. Auto-generated if missing. */
        public String p12Path = "certs/renagc-tls.p12";
        /** PKCS12 / key password. Change this in local config before enabling TLS. */
        public String password = "change-me";
        /** Subject-Alt-Names baked into the generated self-signed cert. */
        public java.util.List<String> sans = new java.util.ArrayList<>(java.util.List.of(
                "localhost",
                "127.0.0.1"));
    }

    /**
     * Master data lives outside git. Point {@link #dir} at the extracted resource
     * pack and set {@link #keyHex} locally when table decryption is needed.
     */
    public static final class Masterdata {
        /** Directory holding encrypted table blobs and MasterDataSystemVersion.txt. */
        public String dir = "resources/masterdata";
        /** Optional served-overrides dir; files here shadow same-named files in dir. */
        public String overrideDir = "resources/masterdata-overrides";
        /** When true, serve the encrypted tables over a local HTTP endpoint. */
        public boolean serveHttp = true;
        /** HTTP port for the master-data endpoint. */
        public int httpPort = 5080;
        /** HTTPS port for the master-data endpoint, used only when TLS is enabled. */
        public int httpsPort = 8443;
        /** 32-byte hex decode key. Leave blank until a local resource pack is configured. */
        public String keyHex = "";
    }

    /**
     * External resource bundles. The public repo ships no bulk resources; users
     * extract a separate archive and point these paths at it.
     */
    public static final class Asset {
        /** When true, serve the asset bundles over a local HTTP endpoint. */
        public boolean serveHttp = true;
        /** HTTP port for the asset endpoint. */
        public int httpPort = 5081;
        /** HTTPS port for the asset endpoint, used only when TLS is enabled. */
        public int httpsPort = 8444;
        /** Primary serving root from the extracted resource pack. */
        public String dir = "resources/asset";
        /** Optional fallback root. Blank disables it. */
        public String embeddedDir = "";
    }

    /**
     * Remote operator console over HTTP. Public defaults are closed; enable it
     * explicitly in local config and set a non-empty token before exposing it.
     */
    public static final class Console {
        /** Master switch for the HTTP remote console. */
        public boolean remoteEnabled = false;
        /** HTTP listen port for the console UI and API. */
        public int remotePort = 5090;
        /** Loopback by default; use 0.0.0.0 only on trusted networks. */
        public String remoteHost = "127.0.0.1";
        /** Required when the remote console is enabled. */
        public String remoteToken = "change-me";
    }

    /** Load from a path, or return defaults (logged) if it is missing/unreadable. */
    public static Config load(Path path) {
        if (path == null || !Files.exists(path)) {
            log.warn("config.json not found at {}; using built-in defaults.", path);
            return new Config();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Config c = GSON.fromJson(r, Config.class);
            if (c == null) {
                log.warn("config.json was empty; using built-in defaults.");
                return new Config();
            }
            if (c.grpc == null) c.grpc = new Grpc();
            if (c.masterdata == null) c.masterdata = new Masterdata();
            if (c.asset == null) c.asset = new Asset();
            if (c.tls == null) c.tls = new Tls();
            if (c.console == null) c.console = new Console();
            log.info("Loaded config from {}", path.toAbsolutePath());
            return c;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse config.json ({}); using built-in defaults.", e.toString());
            return new Config();
        }
    }
}
