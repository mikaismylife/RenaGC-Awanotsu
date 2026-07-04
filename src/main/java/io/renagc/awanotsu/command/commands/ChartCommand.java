package io.renagc.awanotsu.command.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.proto.common.LiveMusic;
import io.renagc.awanotsu.proto.common.LiveScore;
import io.renagc.awanotsu.proto.common.PlayerData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code /chart <lock|unlock|clear|done|fc|ap|afc|reset> <musicId | all> [hscore:<int>]} —
 * set a song/chart's lock + clear state (and optionally its high score) on the active player.
 *
 * <p>State → effect (clear ranks map to {@code TElement.ClearStatus}, dump.cs:253116;
 * difficulties {@code LiveDifficulty} Easy=0/Normal=1/Hard=2/Expert=3, dump.cs:1102553;
 * each song's charts come from {@code MasterLiveMusicScore}, {@code _id = musicId*100 + difficulty},
 * {@code _fullComboCount} = note count):
 * <ul>
 *   <li><b>lock</b>   — song LOCKED: removed from owned ({@code live_music}) + its scores cleared.</li>
 *   <li><b>unlock</b> — song UNLOCKED, uncleared: in {@code live_music}, no {@code live_score}.</li>
 *   <li><b>reset</b>  — wipe play history: scores removed (as if NEVER played, high score 0);
 *       ownership unchanged. {@code hscore} is ignored.</li>
 *   <li><b>done</b>   — every chart {@code clear_status = Failed(1)} (played/attempted).</li>
 *   <li><b>clear</b>  — every chart {@code clear_status = Clear(2)}.</li>
 *   <li><b>afc</b>    — every chart {@code clear_status = AssistFullCombo(3)}.</li>
 *   <li><b>fc</b>     — every chart {@code clear_status = FullCombo(4)}.</li>
 *   <li><b>ap</b>     — every chart {@code clear_status = AllPerfect(5)}.</li>
 * </ul>
 * Any clear state also implies UNLOCKED (the song is added to {@code live_music} if missing).
 * Optional {@code hscore:<int>} sets the high score on every affected chart (clear states only;
 * default is derived from the note count). Target is a single {@code musicId} or {@code all}.
 * Updates the LIVE player and persists; the client shows it on its next GetPlayerData.
 */
@Command(
        label = "chart",
        aliases = {"music", "song"},
        usage = {"<lock|unlock|clear|done|fc|ap|afc|reset> <musicId | all> [diff:<easy|normal|hard|expert|all>] [hscore:<int>]"},
        description = "Set a song's lock/clear state (+ optional diff/hscore) on the active player.")
public final class ChartCommand implements CommandHandler {

    /** TElement.ClearStatus (dump.cs:253116); CS_NONE = sentinel "no clear record". */
    private static final int CS_NONE = 0;
    private static final int CS_FAILED = 1;   // done
    private static final int CS_CLEAR = 2;    // clear
    private static final int CS_AFC = 3;      // afc
    private static final int CS_FC = 4;       // fc
    private static final int CS_AP = 5;       // ap
    private static final int MODE_FREE = 0;

    private final ServerContext ctx;

    public ChartCommand(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void execute(List<String> args, Output out) {
        // Parse: positional <state> <target>, plus optional "hscore:<int>" and
        // "diff:<easy|normal|hard|expert|all>" anywhere. diffIdx null = all difficulties.
        Integer hscore = null;
        Integer diffIdx = null;
        List<String> pos = new ArrayList<>();
        for (String a : args) {
            String t = a.trim();
            String tl = t.toLowerCase();
            if (tl.startsWith("hscore:")) {
                try {
                    hscore = Integer.parseInt(t.substring("hscore:".length()).trim());
                } catch (NumberFormatException e) {
                    out.println("hscore must be an integer (e.g. hscore:1234567).");
                    return;
                }
            } else if (tl.startsWith("diff:")) {
                switch (tl.substring("diff:".length()).trim()) {
                    case "easy" -> diffIdx = 0;
                    case "normal" -> diffIdx = 1;
                    case "hard" -> diffIdx = 2;
                    case "expert" -> diffIdx = 3;
                    case "all", "" -> diffIdx = null;
                    default -> {
                        out.println("diff must be easy|normal|hard|expert|all.");
                        return;
                    }
                }
            } else if (!t.isEmpty()) {
                pos.add(t);
            }
        }
        final Integer diff = diffIdx;
        if (pos.size() < 2) {
            usageHelp(out);
            return;
        }
        String state = pos.get(0).toLowerCase();
        String target = pos.get(1);

        boolean lock = state.equals("lock");
        boolean unlock = state.equals("unlock");
        boolean reset = state.equals("reset");
        int clearStatus = switch (state) {
            case "done" -> CS_FAILED;
            case "clear" -> CS_CLEAR;
            case "afc" -> CS_AFC;
            case "fc" -> CS_FC;
            case "ap" -> CS_AP;
            default -> CS_NONE;
        };
        if (!lock && !unlock && !reset && clearStatus == CS_NONE) {
            out.println("Unknown state '" + state + "'.");
            usageHelp(out);
            return;
        }

        long pid = ctx.players().activeProfile();
        if (pid == 0L) pid = ctx.players().resolveOrRegister(null);
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd == null) {
            out.println("No player " + pid + ".");
            return;
        }

        // Resolve target music ids.
        JsonArray music = ctx.masterData().table("MasterLiveMusic");
        Set<Long> targetIds = new TreeSet<>();
        if (target.equalsIgnoreCase("all")) {
            for (JsonElement e : music) {
                if (!e.isJsonObject()) continue;
                long id = num(e.getAsJsonObject(), "_id");
                if (id != 0) targetIds.add(id);
            }
        } else {
            long id;
            try {
                id = Long.parseLong(target);
            } catch (NumberFormatException nfe) {
                out.println("musicId must be a number or 'all'.");
                return;
            }
            boolean exists = false;
            for (JsonElement e : music) {
                if (e.isJsonObject() && num(e.getAsJsonObject(), "_id") == id) { exists = true; break; }
            }
            if (!exists) {
                out.println("Unknown musicId " + id + " (not in MasterLiveMusic).");
                return;
            }
            targetIds.add(id);
        }
        if (targetIds.isEmpty()) {
            out.println("No target songs.");
            return;
        }

        PlayerData.Builder b = pd.toBuilder();
        long now = System.currentTimeMillis() / 1000L;

        // --- ownership (live_music): lock removes; unlock + clear-states ensure-add; reset leaves it ---
        Map<Long, LiveMusic> owned = new LinkedHashMap<>();
        for (LiveMusic m : b.getLiveMusicList()) owned.put(m.getMusicId(), m);
        if (lock) {
            for (long id : targetIds) owned.remove(id);
        } else if (!reset) {
            for (long id : targetIds) {
                owned.computeIfAbsent(id, k ->
                        LiveMusic.newBuilder().setMusicId(k).setGotAt(now).build());
            }
        }
        b.clearLiveMusic();
        b.addAllLiveMusic(owned.values());

        // --- clear records (live_score): every state first DROPS the targets' existing scores
        // (restricted to the chosen difficulty when diff: is given) ---
        List<LiveScore> kept = new ArrayList<>();
        for (LiveScore s : b.getLiveScoreList()) {
            boolean hit = targetIds.contains(s.getMusicId())
                    && (diff == null || s.getDifficulty() == diff);
            if (!hit) kept.add(s);
        }
        b.clearLiveScore();
        b.addAllLiveScore(kept);

        // lock/unlock/reset leave the songs with NO score record (never-played / hscore 0).
        // A clear state adds fresh per-(music,difficulty) records.
        int chartsTouched = 0;
        if (clearStatus != CS_NONE) {
            for (JsonElement e : ctx.masterData().table("MasterLiveMusicScore")) {
                if (!e.isJsonObject()) continue;
                long scoreId = num(e.getAsJsonObject(), "_id");
                if (scoreId == 0) continue;
                long musicId = scoreId / 100;
                if (!targetIds.contains(musicId)) continue;
                int difficulty = (int) (scoreId % 100);
                if (diff != null && difficulty != diff) continue;
                int fullCombo = (int) num(e.getAsJsonObject(), "_fullComboCount");
                int maxCombo = clearStatus >= CS_AFC ? fullCombo               // AFC/FC/AP = full combo
                        : (fullCombo > 0 ? (fullCombo * 4) / 5 : 0);           // Clear/done = partial
                int highScore = hscore != null ? hscore
                        : clearStatus == CS_FAILED ? (fullCombo > 0 ? fullCombo * 1000 : 0)
                        : (fullCombo > 0 ? fullCombo * 3000 : 1_000_000);
                b.addLiveScore(LiveScore.newBuilder()
                        .setMusicId(musicId)
                        .setDifficulty(difficulty)
                        .setMode(MODE_FREE)
                        .setClearStatus(clearStatus)
                        .setMaxCombo(maxCombo)
                        .setHighScore(highScore)
                        .build());
                chartsTouched++;
            }
        }

        ctx.players().savePlayerData(pid, b.build());

        String scope = target.equalsIgnoreCase("all") ? (targetIds.size() + " songs") : ("song " + target);
        String diffLabel = diff == null ? "all" : switch ((int) diff) {
            case 0 -> "easy"; case 1 -> "normal"; case 2 -> "hard"; case 3 -> "expert"; default -> String.valueOf(diff);
        };
        out.println("/chart " + state + " " + scope + " (diff=" + diffLabel + ") → player " + pid + ": owned="
                + b.getLiveMusicCount()
                + (clearStatus != CS_NONE ? (", charts set=" + chartsTouched
                        + (hscore != null ? (", hscore=" + hscore) : "")) : "")
                + ". Reconnect/refresh the client to see it.");
    }

    private void usageHelp(Output out) {
        out.println("Usage: /chart <lock|unlock|clear|done|fc|ap|afc|reset> <musicId | all> "
                + "[diff:<easy|normal|hard|expert|all>] [hscore:<int>]");
        out.println("  lock=remove song  unlock=own,uncleared  reset=never-played(hscore 0)");
        out.println("  done=Failed(1)  clear=Clear(2)  afc=AssistFC(3)  fc=FullCombo(4)  ap=AllPerfect(5)");
        out.println("  diff:<…> limits to one difficulty (default all). hscore:<int> sets the high score (clear states).");
    }

    /** Read a numeric master field (handles int/long/quoted), 0 if absent. */
    private static long num(JsonObject o, String key) {
        JsonElement v = o.get(key);
        if (v == null || v.isJsonNull()) return 0L;
        try {
            return v.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            try {
                return Long.parseLong(v.getAsString().trim());
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }
}
