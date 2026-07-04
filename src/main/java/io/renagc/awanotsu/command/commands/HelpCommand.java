package io.renagc.awanotsu.command.commands;

import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.Command;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.command.CommandMap;

import java.util.List;

/**
 * /help — lists every registered command. Holds a back-reference to the
 * {@link CommandMap} (injected after construction) so it can enumerate peers.
 */
@Command(
        label = "help",
        aliases = {"?"},
        usage = {""},
        description = "List available commands.")
public final class HelpCommand implements CommandHandler {

    private static volatile CommandMap map;

    public HelpCommand(ServerContext ctx) {
        // ctx unused; map is wired by ConsoleManager after the map is built.
    }

    /** Wired by ConsoleManager once the CommandMap exists. */
    public static void bind(CommandMap commandMap) {
        map = commandMap;
    }

    @Override
    public void execute(List<String> args, Output out) {
        out.println("Available commands:");
        if (map == null) {
            out.println("  (command map not yet bound)");
            return;
        }
        for (CommandHandler h : map.handlers()) {
            Command c = h.meta();
            StringBuilder line = new StringBuilder("  ").append(c.label());
            if (c.aliases().length > 0) {
                line.append(" (").append(String.join(", ", c.aliases())).append(")");
            }
            if (!c.description().isEmpty()) {
                line.append(" - ").append(c.description());
            }
            out.println(line.toString());
        }
        out.println("  stop - shut down the server");
    }
}
