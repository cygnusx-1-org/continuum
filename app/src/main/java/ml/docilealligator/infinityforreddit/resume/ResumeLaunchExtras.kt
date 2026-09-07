package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle

/**
 * A screen whose relaunch extras are not simply the ones it was launched with.
 *
 * Implemented alongside [Restorable] by the handful of screens launched with a `Parcelable` they
 * can rebuild from an identifier -- a post handed whole to the comments screen, say. A marshalled
 * `Parcel` must never be written to disk (its layout is a private implementation detail that
 * changes between releases), so an entry carrying one is otherwise unrecordable and truncates the
 * snapshot. Handing back the identifier instead keeps the screen in the stack at the cost of one
 * refetch.
 *
 * Called before the activity's own `onCreate`, so an implementation may read only its intent.
 *
 * **Usually called off the main thread**, from `ResumeState`'s describe executor, shortly after the
 * screen is created. Reading only the intent is what makes that safe, so it is a hard requirement
 * rather than a description of current practice: no view access, no mutable activity state, nothing
 * that assumes a Looper. An implementation that needs more than its intent cannot be one of these.
 *
 * It may still run on the main thread when a capture reaches the screen before the background work
 * has finished, so it must also be quick enough to survive that -- but the reason the expensive
 * implementations (a `Post`, a `PostFilter` or a `MultiReddit` through Gson) are tolerable at all
 * is that the common path is no longer the main thread.
 */
interface ResumeLaunchExtras {

    /**
     * The extras to relaunch this screen with, or null if it cannot be relaunched at all -- which
     * truncates the snapshot here, exactly as an unencodable extra would.
     */
    fun resumeLaunchExtras(): Bundle?

    /**
     * The part of this screen's identity that is hidden inside a `Parcelable`, cheaply.
     *
     * Only that part. `ResumeState` combines the answer with every primitive extra the screen was
     * launched with, so a query, a tab, a sort order or a subreddit name does not belong here --
     * they are already accounted for, and listing them again would just be a second place to forget
     * one. What it cannot see is the field inside the object: the post's id, the filter's name.
     *
     * Used only when a rebuilt screen is deciding whether it is the one a destroyed entry belonged
     * to. That question was previously answered by comparing [resumeLaunchExtras] against the
     * recorded extras, which meant running the whole object graph through Gson **on the main
     * thread**, during a rotation -- the one place the describe could not simply be moved off it,
     * because the answer decides where the entry sits in the stack and so cannot wait.
     *
     * Answering with an identifier instead costs an unparcel rather than a serialization. Reading a
     * `Parcelable` extra to pull one field out of it is fine here; serializing it is not.
     *
     * Null means "no cheap answer", and the full comparison is used, which is correct but expensive
     * -- so a screen that rewrites a large `Parcelable` in [resumeLaunchExtras] should override
     * this. Two screens of the same class may share a value only when what is left -- their
     * primitive extras -- still tells them apart, since it is the pair that is compared. Where
     * neither differs, a false match adopts the wrong entry and the resume reopens a screen the
     * user was not on.
     */
    fun resumeIdentity(): String? = null
}
