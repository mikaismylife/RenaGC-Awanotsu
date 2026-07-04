package io.renagc.awanotsu.command;

import java.util.List;

/**
 * A console command. Mirrors Grasscutter's CommandHandler ergonomics, but the
 * "sender" is always the local server console (no online Player), so feedback
 * goes straight to the supplied {@link Output} sink.
 */
public interface CommandHandler {

    /** Where a command writes its feedback (console line printer). */
    @FunctionalInterface
    interface Output {
        void println(String message);
    }

    /**
     * Execute the command.
     *
     * @param args parsed arguments (the label itself already stripped)
     * @param out  feedback sink
     */
    void execute(List<String> args, Output out);

    default Command meta() {
        return this.getClass().getAnnotation(Command.class);
    }

    default String getLabel() {
        return meta().label();
    }

    default String getUsageString() {
        Command c = meta();
        StringBuilder sb = new StringBuilder();
        for (String u : c.usage()) {
            sb.append("Usage: ").append(c.label());
            if (!u.isEmpty()) sb.append(' ').append(u);
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    default void sendUsage(Output out) {
        out.println(getUsageString());
    }
}
