package com.heytozzz.htzdistance.selftest;

/** Shared flags between the self-test script (client thread) and its server mixin. */
public final class SelfTestState {
    /** When true, the integrated server uses the target server's tracking ranges. */
    public static volatile boolean serverLikeRanges;

    private SelfTestState() {
    }
}
