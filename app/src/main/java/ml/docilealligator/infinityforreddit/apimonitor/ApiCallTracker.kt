package ml.docilealligator.infinityforreddit.apimonitor

import android.content.SharedPreferences
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Receives captured API calls from [ApiMonitorEventListener] and persists them off the network
 * thread. Old rows are pruned by age so the table cannot grow without bound. Recording can be
 * toggled at runtime via [isEnabled]; the flag is mirrored to shared preferences.
 *
 * Provided as a singleton from AppModule.
 */
class ApiCallTracker(
    private val database: RedditDataRoomDatabase,
    private val sharedPreferences: SharedPreferences,
    private val executor: Executor
) {
    @Volatile
    var isEnabled: Boolean =
        sharedPreferences.getBoolean(SharedPreferencesUtils.API_MONITORING_ENABLED, true)
        private set

    private val sinceLastPrune = AtomicInteger(0)

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        sharedPreferences.edit()
            .putBoolean(SharedPreferencesUtils.API_MONITORING_ENABLED, enabled)
            .apply()
    }

    fun record(record: ApiCallRecord) {
        if (!isEnabled) {
            return
        }
        executor.execute {
            try {
                val dao = database.apiCallRecordDao()
                dao.insert(record)
                if (sinceLastPrune.incrementAndGet() >= PRUNE_EVERY_N_INSERTS) {
                    sinceLastPrune.set(0)
                    dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
                }
            } catch (ignored: Exception) {
                // Monitoring must never crash or interfere with normal app behaviour.
            }
        }
    }

    companion object {
        val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(7)
        private const val PRUNE_EVERY_N_INSERTS = 200
    }
}
