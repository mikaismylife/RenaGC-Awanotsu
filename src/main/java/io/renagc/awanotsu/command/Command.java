package io.renagc.awanotsu.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link CommandHandler} for discovery by {@link CommandMap}.
 * Grasscutter-style {@code @Command}, trimmed to the fields this console needs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {
    /** Primary command label, e.g. "give". */
    String label();

    /** Alternate labels, e.g. {"g"}. */
    String[] aliases() default {};

    /** One-line usage string(s) shown by /help and on bad input. */
    String[] usage() default {""};

    /** Short description shown by /help. */
    String description() default "";
}
