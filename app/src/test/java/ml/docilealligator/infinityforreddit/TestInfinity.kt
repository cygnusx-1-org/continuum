package ml.docilealligator.infinityforreddit

/**
 * Minimal application for Robolectric activity tests. The real [Infinity.onCreate] installs a
 * process-global EventBus (set-once, so it throws on the second test) and starts background
 * receivers that outlive the test; this builds only the Dagger graph the activities inject from.
 *
 * Fidelity limits, so a future test leaning on this knows what is missing rather than hitting a
 * silent null. Deliberately NOT initialised here:
 *  - the static `Infinity.instance`, so [Infinity.getAppContext] returns null (its only callers are
 *    in SavedPostCache; a test exercising that needs to set the field or call super.onCreate);
 *  - the activity lifecycle callbacks that apply the custom font and secure-mode flag;
 *  - the network/notification receivers and WorkManager the real app starts.
 */
class TestInfinity : Infinity() {

    private lateinit var testComponent: AppComponent

    override fun onCreate() {
        // Deliberately not super.onCreate(): see the class comment. Application.onCreate() is a
        // no-op we can skip -- Robolectric sets the app up itself.
        testComponent = DaggerAppComponent.factory().create(this)
        testComponent.inject(this)
    }

    override fun getAppComponent(): AppComponent = testComponent
}
