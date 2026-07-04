package io.renagc.awanotsu.command;

import io.renagc.awanotsu.ServerContext;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Discovers every {@link Command}-annotated {@link CommandHandler} via
 * Reflections and dispatches a raw input line to the matching handler.
 * Faithful to Grasscutter's CommandMap scan/registry pattern, with local
 * operator-console semantics.
 */
public final class CommandMap {

    private static final Logger log = LoggerFactory.getLogger(CommandMap.class);

    private final Map<String, CommandHandler> byLabel = new TreeMap<>();
    private final ServerContext ctx;

    public CommandMap(ServerContext ctx) {
        this.ctx = ctx;
        scan();
    }

    private void scan() {
        Reflections reflections = new Reflections("io.renagc.awanotsu.command.commands");
        var classes = reflections.getTypesAnnotatedWith(Command.class);
        for (Class<?> clazz : classes) {
            if (!CommandHandler.class.isAssignableFrom(clazz)) continue;
            Command meta = clazz.getAnnotation(Command.class);
            try {
                CommandHandler handler = instantiate(clazz);
                register(meta.label(), handler);
                for (String alias : meta.aliases()) {
                    register(alias, handler);
                }
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to register command {}: {}", clazz.getSimpleName(), e.toString());
            }
        }
        log.info("Registered {} command label(s).", byLabel.size());
    }

    private CommandHandler instantiate(Class<?> clazz) throws ReflectiveOperationException {
        // Prefer a (ServerContext) constructor; fall back to no-arg.
        try {
            return (CommandHandler) clazz.getConstructor(ServerContext.class).newInstance(ctx);
        } catch (NoSuchMethodException ignored) {
            return (CommandHandler) clazz.getConstructor().newInstance();
        }
    }

    private void register(String label, CommandHandler handler) {
        if (label == null || label.isBlank()) return;
        byLabel.put(label.toLowerCase(), handler);
    }

    /** Distinct handlers (one per command, aliases collapsed), label-sorted. */
    public List<CommandHandler> handlers() {
        Map<CommandHandler, Boolean> seen = new LinkedHashMap<>();
        List<CommandHandler> out = new ArrayList<>();
        for (CommandHandler h : byLabel.values()) {
            if (seen.putIfAbsent(h, Boolean.TRUE) == null) out.add(h);
        }
        return out;
    }

    /** Parse and run a raw console line. Returns false on unknown command. */
    public boolean invoke(String line, CommandHandler.Output out) {
        if (line == null) return true;
        line = line.trim();
        if (line.isEmpty()) return true;
        // Allow an optional leading slash, Grasscutter-style.
        if (line.startsWith("/")) line = line.substring(1);

        String[] parts = line.split("\\s+");
        String label = parts[0].toLowerCase();
        List<String> args = new ArrayList<>(List.of(parts).subList(1, parts.length));

        CommandHandler handler = byLabel.get(label);
        if (handler == null) {
            out.println("Unknown command: " + label + " (type 'help')");
            return false;
        }
        try {
            handler.execute(args, out);
        } catch (RuntimeException e) {
            out.println("Command error: " + e.getMessage());
            log.warn("Command '{}' threw", label, e);
        }
        return true;
    }
}
