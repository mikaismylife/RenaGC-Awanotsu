package io.renagc.awanotsu.console;

import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.command.CommandHandler;
import io.renagc.awanotsu.command.CommandMap;
import io.renagc.awanotsu.command.commands.HelpCommand;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JLine console REPL. Reads a line, hands it to {@link CommandMap#invoke}, and
 * prints feedback. Lifted from Grasscutter's startConsole ergonomics:
 * {@code readLine("> ")} loop, EOF/Ctrl-C handling, a {@code stop} command.
 *
 * <p>If no real terminal is attached (e.g. piped stdin / no TTY) it degrades to
 * a plain {@code System.in} reader so the server still runs headless.
 */
public final class ConsoleManager {

    private static final Logger log = LoggerFactory.getLogger(ConsoleManager.class);

    private final CommandMap commandMap;
    private volatile boolean running = true;
    private Runnable onStop = () -> {};

    public ConsoleManager(ServerContext ctx) {
        this.commandMap = new CommandMap(ctx);
        HelpCommand.bind(commandMap);
    }

    /** Action to run when the user types 'stop' or sends EOF. */
    public void onStop(Runnable onStop) {
        this.onStop = onStop;
    }

    /** Start the REPL on a daemon thread so it never blocks server shutdown. */
    public void startAsync() {
        Thread t = new Thread(this::loop, "renagc-console");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        CommandHandler.Output out = System.out::println;
        Terminal terminal;
        try {
            terminal = TerminalBuilder.builder().dumb(true).build();
        } catch (IOException e) {
            log.warn("Console terminal unavailable ({}); console disabled.", e.toString());
            return;
        }
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        log.info("Console ready. Type 'help' for commands, 'stop' to shut down.");
        while (running) {
            String line;
            try {
                line = reader.readLine("> ");
            } catch (EndOfFileException eof) {
                // No interactive stdin (headless / piped / no TTY). Disable the console but
                // KEEP the server running — an EOF must NOT self-terminate a headless instance
                // (explicit 'stop'/'exit' or Ctrl-C below still shut down a real console).
                log.info("Console EOF (no interactive TTY); console disabled, server stays up.");
                running = false;
                return;
            } catch (UserInterruptException ui) {
                log.info("Console interrupted; shutting down.");
                stop();
                return;
            }
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.equalsIgnoreCase("stop") || trimmed.equalsIgnoreCase("exit")) {
                stop();
                return;
            }
            commandMap.invoke(trimmed, out);
        }
    }

    private void stop() {
        running = false;
        try {
            onStop.run();
        } catch (RuntimeException e) {
            log.warn("onStop hook failed: {}", e.toString());
        }
    }
}
