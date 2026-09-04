package ml.docilealligator.infinityforreddit.account

import android.content.SharedPreferences

/**
 * A [SharedPreferences] that stores some of its keys per account.
 *
 * The app reads its settings from an injected `SharedPreferences` in over a hundred places, so the
 * account is applied here, once, rather than at every read site: a key the [isScoped] policy calls
 * per-account is rewritten to its [AccountScope] spelling on the way through, and every other key
 * passes straight down. Callers cannot tell the difference, which is the point — it keeps this
 * change out of the files an upstream merge touches.
 *
 * There is no fallback to a global value. A per-account setting is whatever this account stored, or
 * the default the caller passed; existing accounts were given the old global value once by
 * [AccountSettingsMigration], and accounts added later start at the code defaults.
 */
class AccountScopedSharedPreferences(
    private val delegate: SharedPreferences,
    private val isScoped: (String) -> Boolean,
    private val currentAccountName: () -> String?,
) : SharedPreferences {

    private val listeners =
        mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener,
                SharedPreferences.OnSharedPreferenceChangeListener>()

    private fun resolve(key: String): String =
        if (isScoped(key)) AccountScope.key(currentAccountName(), key) else key

    /**
     * The stored key [key] came back as, seen from this account: `null` for anything belonging to a
     * different account, or for the inert pre-migration copy of a scoped key.
     */
    private fun unresolve(key: String): String? {
        // The namespaced spelling is decided on first. Asking isScoped(key) up front would be
        // wrong for the whole-file-scoped files, where it answers true for every key including one
        // already spelled `alice.sort_type_best_post` — every key would unresolve to null there.
        val base = AccountScope.baseOf(key)
            // No namespace, so this can be the original global value: left in place by the
            // migration but no longer authoritative once the key is one we scope.
            ?: return if (isScoped(key)) null else key
        if (!isScoped(base)) {
            return key
        }
        return if (AccountScope.namespaceOf(key) == AccountScope.namespace(currentAccountName())) {
            base
        } else {
            null
        }
    }

    override fun getAll(): MutableMap<String, Any?> {
        val all = mutableMapOf<String, Any?>()
        for ((key, value) in delegate.all) {
            unresolve(key)?.let { all[it] = value }
        }
        return all
    }

    override fun getString(key: String, defValue: String?): String? =
        delegate.getString(resolve(key), defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        delegate.getStringSet(resolve(key), defValues)

    override fun getInt(key: String, defValue: Int): Int = delegate.getInt(resolve(key), defValue)

    override fun getLong(key: String, defValue: Long): Long = delegate.getLong(resolve(key), defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        delegate.getFloat(resolve(key), defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        delegate.getBoolean(resolve(key), defValue)

    override fun contains(key: String): Boolean = delegate.contains(resolve(key))

    override fun edit(): SharedPreferences.Editor = ScopedEditor(delegate.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        // Idempotent, as the platform class this impersonates is: it keys a WeakHashMap by the
        // listener. Replacing the entry instead would strand the previous wrapper on the delegate,
        // where it can never be unregistered and doubles every callback until then.
        if (listeners.containsKey(listener)) {
            return
        }

        // A listener hears about its own account's keys, under the names it asked for. A null key
        // means the file was cleared, and is passed on as-is.
        val translating = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null) {
                listener.onSharedPreferenceChanged(this, null)
            } else {
                unresolve(changedKey)?.let { listener.onSharedPreferenceChanged(this, it) }
            }
        }
        listeners[listener] = translating
        delegate.registerOnSharedPreferenceChangeListener(translating)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)?.let(delegate::unregisterOnSharedPreferenceChangeListener)
    }

    private inner class ScopedEditor(private val delegate: SharedPreferences.Editor) :
        SharedPreferences.Editor {

        override fun putString(key: String, value: String?) =
            also { delegate.putString(resolve(key), value) }

        override fun putStringSet(key: String, values: MutableSet<String>?) =
            also { delegate.putStringSet(resolve(key), values) }

        override fun putInt(key: String, value: Int) = also { delegate.putInt(resolve(key), value) }

        override fun putLong(key: String, value: Long) = also { delegate.putLong(resolve(key), value) }

        override fun putFloat(key: String, value: Float) =
            also { delegate.putFloat(resolve(key), value) }

        override fun putBoolean(key: String, value: Boolean) =
            also { delegate.putBoolean(resolve(key), value) }

        override fun remove(key: String) = also { delegate.remove(resolve(key)) }

        /**
         * Clears the whole file, other accounts included. Only the global "Reset All Settings" and
         * the logout path do this, and both mean all of it; a per-account reset removes that
         * account's keys instead.
         */
        override fun clear() = also { delegate.clear() }

        override fun commit(): Boolean = delegate.commit()

        override fun apply() = delegate.apply()
    }
}
