package com.heytozzz.htzdistance.render;

/** Temporary switches used by the self-test bench to split the ghost cost. */
public final class BenchFlags {
    /** Skip restoring (or resolving) the items of reused render states. */
    public static volatile boolean skipItemRefresh;
    /** Resolve the items of reused render states every frame instead of restoring a copy. */
    public static volatile boolean forceItemResolve;
    /** Do everything except handing the states to vanilla (no submit, no draw). */
    public static volatile boolean skipSubmit;

    private BenchFlags() {
    }
}
