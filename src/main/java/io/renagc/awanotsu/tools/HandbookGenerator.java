package io.renagc.awanotsu.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.renagc.awanotsu.Config;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.master.MasterDataStore;
import org.reflections.Reflections;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a <b>GM Handbook</b> — the console command list plus {@code id : name}
 * maps for every relevant master table — as a plain {@code GM Handbook - <LANG>.txt},
 * in the spirit of Grasscutter's {@code Tools.createGmHandbooks()}.
 *
 * <p><b>This is NOT a 1:1 port of Grasscutter's generator</b> (RenaGC is a hybrid
 * gRPC + CDN server — see the README), but it mirrors the same idea: resolve display
 * names through a text table, then dump padded {@code id : name} sections. Awanotsu
 * keeps its display strings in the {@code MasterText} table, which carries five
 * columns — {@code _japanese}, {@code _english}, {@code _traditionalChinese},
 * {@code _simplifiedChinese}, {@code _korean} — keyed by the {@code *TextId} fields on
 * each row (e.g. {@code MasterItem._nameTextId = "Item_Name_1"}). {@code <LANG>}
 * selects the column (EN/JP/CHS/CHT/KR), falling back to Japanese when a cell is empty.
 *
 * <p><b>Command descriptions</b> are taken verbatim from each {@code @Command} (they are
 * authored in English in the source); only the master-data <i>names</i> are localized.
 *
 * <p>Run via Gradle: {@code gradlew generateHandbook} (EN) or
 * {@code gradlew generateHandbook --args="CHS"}. Output:
 * {@code ./GM Handbook/GM Handbook - <LANG>.txt}. Needs {@code config.json}
 * ({@code masterdata.dir} + {@code keyHex}); no running server.
 */
public final class HandbookGenerator {

    /** A handbook section sourced from one master table: title, table, id field, text-key field(s). */
    private record Category(String title, String table, String idField, List<String> keyFields) {
        Category(String title, String table, String idField, String... keyFields) {
            this(title, table, idField, List.of(keyFields));
        }
    }

    /**
     * Master tables mapped into the handbook. Absent/empty tables are skipped. The name
     * comes from the first present text-key field, resolved through {@code MasterText}.
     * (Field-name casing genuinely varies across tables: {@code _nameTextId} vs
     * {@code _nameTextID} vs {@code _titleTextID} vs {@code _skinNameTextId}.)
     */
    private static final List<Category> CATEGORIES = List.of(
            new Category("Items", "MasterItem", "_id", "_nameTextId", "_nameTextID"),
            new Category("Songs (Live Music)", "MasterLiveMusic", "_id", "_titleTextID", "_titleTextId"),
            new Category("Member Cards", "MasterMemberCard", "_id", "_nameTextID", "_nameTextId"),
            new Category("Support Cards", "MasterSupportCard", "_id", "_nameTextID", "_nameTextId"),
            new Category("Characters", "MasterCharacter", "_id", "_nameTextID", "_nameTextId"),
            new Category("Bands", "MasterBand", "_id", "_nameTextID", "_nameTextId"),
            new Category("Band Items", "MasterBandItem", "_id", "_nameTextId", "_nameTextID"),
            new Category("Stamps", "MasterStamp", "_id", "_nameTextId", "_nameTextID"),
            new Category("Live Lane Skins", "MasterLiveLaneSkin", "_id", "_skinNameTextId", "_skinNameTextID"),
            new Category("Live Note Skins", "MasterLiveNoteSkin", "_id", "_skinNameTextId", "_skinNameTextID"),
            new Category("Live Note Effect Skins", "MasterLiveNoteEffectSkin", "_id", "_skinNameTextId"),
            new Category("Live Note SE Groups", "MasterLiveNoteSeGroup", "_id", "_skinNameTextId"),
            new Category("Gacha", "MasterGacha", "_id", "_nameTextID", "_nameTextId", "_titleTextID"));

    /** MasterText column for a given handbook language, plus localized section headers. */
    private record Lang(String column, String title, String commands) {
        static Lang of(String lang) {
            return switch (lang) {
                case "JP", "JA", "JA-JP" ->
                        new Lang("_japanese", "RenaGC-Awanotsu GM ハンドブック", "コマンド");
                case "CHS", "ZH-CN", "ZH" ->
                        new Lang("_simplifiedChinese", "RenaGC-Awanotsu GM 手册", "命令");
                case "CHT", "ZH-TW" ->
                        new Lang("_traditionalChinese", "RenaGC-Awanotsu GM 手冊", "指令");
                case "KR", "KO", "KO-KR" ->
                        new Lang("_korean", "RenaGC-Awanotsu GM 핸드북", "명령어");
                default -> new Lang("_english", "RenaGC-Awanotsu GM Handbook", "Commands");
            };
        }
    }

    public static void main(String[] args) throws Exception {
        String lang = (args.length > 0 ? args[0] : "EN").toUpperCase(Locale.ROOT);
        Lang L = Lang.of(lang);

        Config cfg = Config.load(Path.of("config.json"));
        MasterDataStore md = MasterDataStore.open(
                cfg.masterdata.dir, cfg.masterdata.overrideDir, cfg.masterdata.keyHex, cfg.masterVersion);

        // text-key -> {selected-language text, japanese fallback}
        Map<String, String[]> text = loadText(md, L.column());

        StringBuilder out = new StringBuilder();
        out.append("// ").append(L.title()).append("  (lang=").append(lang).append(")\n");
        out.append("// master version ").append(md.version()).append('\n');
        out.append("// Created ")
                .append(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()))
                .append('\n');
        if (!md.available()) {
            out.append("// WARNING: master dir unavailable — only the command list is populated.\n");
        }
        out.append("// Names resolve through MasterText[").append(L.column())
                .append("]; the source data leaves most non-Japanese cells blank or as an untranslated\n")
                .append("// placeholder, so names fall back to the real Japanese title. Command descriptions are English (source).\n");

        // --- Commands (scanned from @Command; no running server / ServerContext needed) ---
        out.append("\n\n// ").append(L.commands()).append('\n');
        List<Command> cmds = scanCommands();
        cmds.sort(Comparator.comparing(Command::label));
        int padCmd = cmds.stream().mapToInt(c -> c.label().length()).max().orElse(8);
        for (Command c : cmds) {
            out.append(padLeft(c.label(), padCmd)).append(" : ").append(c.description());
            if (c.aliases().length > 0) out.append("   [aliases: ").append(String.join(", ", c.aliases())).append(']');
            out.append('\n');
            for (String u : c.usage()) {
                if (u != null && !u.isBlank()) {
                    out.append(" ".repeat(padCmd)).append("     ").append(c.label()).append(' ').append(u).append('\n');
                }
            }
        }

        // --- Master-table id -> name maps ---
        for (Category cat : CATEGORIES) {
            JsonArray rows = md.table(cat.table());
            if (rows == null || rows.isEmpty()) continue;
            String keyField = detectKeyField(rows, cat.keyFields(), text);

            out.append("\n\n// ").append(cat.title())
                    .append("   (").append(cat.table()).append(", ").append(rows.size()).append(" rows)\n");

            int padId = 1;
            for (JsonElement e : rows) {
                if (e.isJsonObject()) {
                    String id = str(e.getAsJsonObject(), cat.idField());
                    if (!id.isEmpty()) padId = Math.max(padId, id.length());
                }
            }
            for (JsonElement e : rows) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = str(o, cat.idField());
                if (id.isEmpty()) continue;
                String name = keyField == null ? "" : resolve(text, str(o, keyField));
                if (name.isBlank()) out.append(padLeft(id, padId)).append('\n');
                else out.append(padLeft(id, padId)).append(" : ").append(name).append('\n');
            }
        }

        Path dir = Path.of("GM Handbook");
        Files.createDirectories(dir);
        Path file = dir.resolve("GM Handbook - " + lang + ".txt");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        System.out.println("GM Handbook written: " + file.toAbsolutePath()
                + "  (" + out.length() + " chars, " + cmds.size() + " commands, " + text.size() + " text keys)");
    }

    /** MasterText -> map of text key to [selected language text, japanese fallback]. */
    private static Map<String, String[]> loadText(MasterDataStore md, String column) {
        Map<String, String[]> map = new HashMap<>();
        for (JsonElement e : md.table("MasterText")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String key = str(o, "_id");
            if (key.isEmpty()) continue;
            map.put(key, new String[] {str(o, column), str(o, "_japanese")});
        }
        return map;
    }

    /**
     * Resolve a text key to display text: prefer the selected language, fall back to
     * Japanese, then the raw key. The source data often leaves non-Japanese cells as an
     * untranslated identifier placeholder that echoes the key (e.g. {@code "Music_Tilte_1"},
     * {@code "Item_Name_30"}) instead of blank — {@link #isReal} treats those as absent so
     * songs/items resolve to their real Japanese name rather than the placeholder.
     */
    private static String resolve(Map<String, String[]> text, String key) {
        if (key == null || key.isBlank()) return "";
        String[] v = text.get(key);
        if (v == null) return key; // no MasterText row — surface the key rather than hide it
        if (isReal(v[0])) return v[0]; // selected language
        if (isReal(v[1])) return v[1]; // Japanese fallback
        return key;
    }

    /** Real display text = non-blank and not an identifier placeholder (word chars + an underscore). */
    private static boolean isReal(String s) {
        return s != null && !s.isBlank() && !s.matches("[A-Za-z0-9]*_[A-Za-z0-9_]*");
    }

    private static List<Command> scanCommands() {
        Reflections refl = new Reflections("io.renagc.awanotsu.command.commands");
        List<Command> out = new ArrayList<>();
        for (Class<?> clz : refl.getTypesAnnotatedWith(Command.class)) {
            Command c = clz.getAnnotation(Command.class);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** First candidate text-key field that is present + resolves to real text in the first rows. */
    private static String detectKeyField(JsonArray rows, List<String> candidates, Map<String, String[]> text) {
        int limit = Math.min(20, rows.size());
        for (String f : candidates) {
            for (int i = 0; i < limit; i++) {
                JsonElement e = rows.get(i);
                if (e.isJsonObject() && !str(e.getAsJsonObject(), f).isBlank()) return f;
            }
        }
        return null;
    }

    private static String str(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return "";
        return e.getAsString();
    }

    private static String padLeft(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    private HandbookGenerator() {}
}
