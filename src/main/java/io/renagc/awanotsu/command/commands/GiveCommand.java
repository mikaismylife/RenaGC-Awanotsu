package io.renagc.awanotsu.command.commands;

import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.proto.common.Gem;
import io.renagc.awanotsu.proto.common.Item;
import io.renagc.awanotsu.proto.common.PlayerData;

import java.util.List;

/**
 * {@code /give gem <amount>}        — add free gems to the active player.
 * {@code /give <itemMasterId> [xN]} — add/merge an item into the active player's inventory.
 *
 * <p>The console is operator-level, so this mutates the
 * {@link io.renagc.awanotsu.persistence.PlayerStore} player state DIRECTLY, targeting
 * {@code activeProfile()} — the most-recently-registered player, i.e. the live client
 * once it has connected (falling back to the auto-created default player offline).
 * Gems live in {@code PlayerData.gem} (field 6, NOT stripped from GetPlayerData), so a
 * gem grant is visible to the client on its next GetPlayerData; items mutate state for
 * the item-delivery surface.
 */
@Command(
        label = "give",
        aliases = {"g"},
        usage = {"gem <amount>", "<itemMasterId> [xN]"},
        description = "Grant gems or an item to the active player (live state mutation).")
public final class GiveCommand implements CommandHandler {

    private final ServerContext ctx;

    public GiveCommand(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void execute(List<String> args, Output out) {
        if (args.isEmpty()) {
            sendUsage(out);
            return;
        }
        long pid = ctx.players().activeProfile();
        if (pid == 0L) pid = ctx.players().resolveOrRegister(null); // create/target the default player
        PlayerData pd = ctx.players().getPlayerData(pid);
        if (pd == null) {
            out.println("No player " + pid + " to grant to.");
            return;
        }
        PlayerData.Builder b = pd.toBuilder();

        // /give gem <amount>
        if (args.get(0).equalsIgnoreCase("gem")) {
            int amount = args.size() > 1 ? parseInt(args.get(1)) : 1;
            Gem g = b.getGem();
            b.setGem(g.toBuilder().setFree(g.getFree() + amount).build());
            ctx.players().savePlayerData(pid, b.build());
            out.println("Gave " + amount + " free gems to player " + pid
                    + " (free=" + b.getGem().getFree() + " paid=" + b.getGem().getPaid() + ").");
            return;
        }

        // /give <itemMasterId> [xN]
        long itemId = parseLong(args.get(0));
        int amount = 1;
        for (String a : args) {
            if (a.length() > 1 && (a.charAt(0) == 'x' || a.charAt(0) == 'X')) {
                amount = parseInt(a.substring(1));
            }
        }
        int idx = -1;
        for (int i = 0; i < b.getItemsCount(); i++) {
            if (b.getItems(i).getMasterId() == itemId) { idx = i; break; }
        }
        int total;
        if (idx >= 0) {
            Item it = b.getItems(idx);
            total = it.getAmount() + amount;
            b.setItems(idx, it.toBuilder().setAmount(total).build());
        } else {
            total = amount;
            b.addItems(Item.newBuilder().setMasterId(itemId).setAmount(amount).build());
        }
        ctx.players().savePlayerData(pid, b.build());
        out.println("Gave item " + itemId + " x" + amount + " to player " + pid
                + " (now have " + total + ").");
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
