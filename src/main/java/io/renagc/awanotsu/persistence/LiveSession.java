package io.renagc.awanotsu.persistence;

/**
 * Transient per-player "currently playing" state. The live flow is stateful: the
 * client sends the music/difficulty/deck in {@code StartFree}, but {@code FinishFree}
 * carries only the score/combo/judgements — so the server must remember which song
 * is in progress between the two calls. Held in memory only (a live in progress does
 * not survive a server restart).
 */
public record LiveSession(long musicId, int difficulty, int boost, int deckId, long startedAt) {
}
