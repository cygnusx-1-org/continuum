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

    /** Never asked. A logged-in account. */
    object Never : InfoUse
}
