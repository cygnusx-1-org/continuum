package ml.docilealligator.infinityforreddit.thing

import java.util.concurrent.atomic.AtomicBoolean

/**
 * How a lookup by id uses `/api/info`, which Reddit has refused for some client IDs since around
 * 2026-10-29 (issue #476).
 */
internal sealed interface InfoUse {

    /**
     * Asked first, and once it has refused with a 403, not again while [blocked] is set. The
     * anonymous account, which has no setting for it.
     */
    class First(val blocked: AtomicBoolean) : InfoUse

    /**
     * Asked alone, as before the block, and whatever it answers is the answer: one request where
     * the fallback makes one per thing, but a failure while Reddit refuses it. A logged-in account
     * with "Use /api/info the Old Way" on.
     */
    object Only : InfoUse

    /** Never asked. A logged-in account with "Use /api/info the Old Way" off, the default. */
    object Never : InfoUse

    companion object {
        /** For a logged-in account, from `APIUtils.useOldApiInfo`. */
        fun forAccount(useOldApiInfo: Boolean): InfoUse = if (useOldApiInfo) Only else Never
    }
}
