package ml.docilealligator.infinityforreddit.search

/**
 * Whether the search field asks the keyboard not to learn what is typed into it.
 *
 * Held in memory rather than in preferences on purpose. Incognito is a decision about the session
 * in front of you, not a setting you configure once, so it outlives a single visit to the search
 * screen -- open search again, or come back to it after a rotation, and it is still on -- but it
 * goes away with the process. Nothing on disk records that it was ever turned on.
 *
 * Read and written on the main thread, from the search screen only.
 */
object IncognitoKeyboardState {

    /** True while the flag is on. Off for the first search of every app launch. */
    @JvmStatic
    var isEnabled: Boolean = false
}
