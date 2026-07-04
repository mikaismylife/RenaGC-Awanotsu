package io.renagc.awanotsu.command.commands;

import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.proto.common.PlayerData;

import java.util.List;

/**
 * /giveall — max the active player's free gems and report the full inventory. The
 * fresh player already carries all member cards / support cards / songs (built from
 * masterdata by {@code FreshPlayerFactory}), so this tops up the spendable currency
 * and confirms the account is fully stocked.
 */
@Command(
        label = "giveall",
        aliases = {"ga"},
        usage = {""},
        description = "Max gems on the active player + report full inventory.")
public final class GiveAllCommand implements CommandHandler {

    private static final int MAX_GEMS = 9_999_999;

    private final ServerContext ctx;

    public GiveAllCommand(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void execute(List<String> args, Output out) {
        long pid = ctx.players().activeProfile();
        if (pid == 0L) pid = ctx.players().resolveOrRegister(null);
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd == null) {
            out.println("No player " + pid + ".");
            return;
        }
        PlayerData.Builder b = pd.toBuilder();
        b.setGem(b.getGem().toBuilder().setFree(MAX_GEMS).build());
        ctx.players().savePlayerData(pid, b.build());
        out.println("Maxed player " + pid + ": gems free=" + MAX_GEMS
                + ", cards=" + b.getMemberCardsCount()
                + ", support=" + b.getSupportCardsCount()
                + ", songs=" + b.getLiveMusicCount()
                + ", decks=" + b.getDecksCount() + ".");
    }
}
