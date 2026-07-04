package io.renagc.awanotsu.tools;

import io.renagc.awanotsu.Config;
import io.renagc.awanotsu.master.MasterDataStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev tool: decrypt named master tables to plaintext JSON on disk, for inspecting
 * what values live in masterdata.
 *
 * <p>Run: {@code gradlew dumpTables --args="MasterLiveSettings MasterLiveNoteParameter ..."}
 * (or no args for a default set). Output: {@code <outDir>/<Table>.json}
 * (default {@code _tmp/master_dump}). Needs {@code config.json}; no running server.
 */
public final class DumpTable {

    private static final String[] DEFAULTS = {
            "MasterLiveSettings", "MasterLiveNoteParameter", "MasterLiveNoteSe",
            "MasterLiveJudgementParameter", "MasterLiveJudgementAreaOffset",
            "MasterLiveJudgementTiming", "MasterLiveJudgementSprite",
            "MasterLiveQualitySettings", "MasterLiveScoreRank", "MasterLiveComboScoreBonus",
            "MasterOptionDefault", "MasterOptionRange", "MasterOptionPresetDefault",
            "MasterLiveLaneSkin", "MasterLiveNoteSkin", "MasterLiveNoteEffectSkin",
    };

    public static void main(String[] args) throws Exception {
        Config cfg = Config.load(Path.of("config.json"));
        MasterDataStore md = MasterDataStore.open(
                cfg.masterdata.dir, cfg.masterdata.overrideDir, cfg.masterdata.keyHex, cfg.masterVersion);

        String outArg = null;
        java.util.List<String> tables = new java.util.ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--out=")) outArg = a.substring(6);
            else tables.add(a);
        }
        if (tables.isEmpty()) tables = java.util.List.of(DEFAULTS);

        Path out = Path.of(outArg != null ? outArg : "_tmp/master_dump");
        Files.createDirectories(out);

        for (String t : tables) {
            String name = t.endsWith(".bin") ? t.substring(0, t.length() - 4) : t;
            try {
                String json = md.decryptTableFile(name + ".bin");
                Files.writeString(out.resolve(name + ".json"), json, StandardCharsets.UTF_8);
                System.out.println("dumped " + name + "  (" + json.length() + " chars)");
            } catch (Exception e) {
                System.out.println("FAIL   " + name + ": " + e.getMessage());
            }
        }
        System.out.println("-> " + out.toAbsolutePath());
    }

    private DumpTable() {}
}
