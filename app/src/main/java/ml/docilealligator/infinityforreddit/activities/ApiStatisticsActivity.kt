package ml.docilealligator.infinityforreddit.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.adapters.ApiStatListItem
import ml.docilealligator.infinityforreddit.adapters.ApiStatisticsRecyclerViewAdapter
import ml.docilealligator.infinityforreddit.adapters.WindowValue
import ml.docilealligator.infinityforreddit.apimonitor.ApiCallTracker
import ml.docilealligator.infinityforreddit.apimonitor.ApiCallWindowStat
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ActivityApiStatisticsBinding
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

/**
 * Displays recorded Reddit API and media-retrieval statistics, grouped by section and endpoint.
 * Each endpoint shows a bar chart of average response time across the last 15 minutes / 1 hour /
 * 1 day / 1 week, so the user can tell at a glance whether things are slower right now than the
 * longer-term baseline, and which endpoints or media hosts are affected.
 */
class ApiStatisticsActivity : BaseActivity() {

    @Inject
    lateinit var redditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    @field:Named("default")
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    @field:Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper

    @Inject
    lateinit var executor: Executor

    @Inject
    lateinit var apiCallTracker: ApiCallTracker

    private lateinit var binding: ActivityApiStatisticsBinding
    private lateinit var adapter: ApiStatisticsRecyclerViewAdapter
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as Infinity).appComponent.inject(this)
        super.onCreate(savedInstanceState)

        binding = ActivityApiStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyCustomTheme()

        setSupportActionBar(binding.toolbarApiStatisticsActivity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.api_statistics_activity_label)

        adapter = ApiStatisticsRecyclerViewAdapter(this, mCustomThemeWrapper)
        binding.recyclerViewApiStatisticsActivity.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApiStatisticsActivity.adapter = adapter

        binding.checkboxGoodApiStatisticsActivity.setOnCheckedChangeListener { _, _ -> updateFilter() }
        binding.checkboxMarginalApiStatisticsActivity.setOnCheckedChangeListener { _, _ -> updateFilter() }
        binding.checkboxBadApiStatisticsActivity.setOnCheckedChangeListener { _, _ -> updateFilter() }

        loadStats()
    }

    private fun updateFilter() {
        adapter.setFilter(
            binding.checkboxGoodApiStatisticsActivity.isChecked,
            binding.checkboxMarginalApiStatisticsActivity.isChecked,
            binding.checkboxBadApiStatisticsActivity.isChecked
        )
    }

    private fun loadStats() {
        executor.execute {
            val now = System.currentTimeMillis()
            val stats = redditDataRoomDatabase.apiCallRecordDao().getWindowStats(
                now - TimeUnit.MINUTES.toMillis(15),
                now - TimeUnit.HOURS.toMillis(1),
                now - TimeUnit.DAYS.toMillis(1),
                now - TimeUnit.DAYS.toMillis(7)
            )
            val items = buildItems(stats)
            handler.post {
                adapter.setItems(items)
                val empty = items.isEmpty()
                binding.emptyTextViewApiStatisticsActivity.visibility =
                    if (empty) View.VISIBLE else View.GONE
                binding.recyclerViewApiStatisticsActivity.visibility =
                    if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun buildItems(stats: List<ApiCallWindowStat>): List<ApiStatListItem> {
        val bySection = stats.groupBy { it.section }

        // Busiest sections (by week volume) first.
        val orderedSections = bySection.entries.sortedByDescending { entry ->
            entry.value.sumOf { it.count1w }
        }

        val items = ArrayList<ApiStatListItem>()
        for (entry in orderedSections) {
            val section = entry.key
            val sectionStats = entry.value
            val headerWindows = aggregateWindows(sectionStats)

            // Scale shared within the section so its rows are comparable and the bars fill the chart.
            val rowWindowsList = sectionStats.map { rowWindows(it) }
            val sectionScaleMax = (rowWindowsList.flatten() + headerWindows)
                .mapNotNull { it.avgMs }.maxOrNull() ?: 0.0

            items.add(
                ApiStatListItem.Header(
                    section = section,
                    totalCount = sectionStats.sumOf { it.count1w },
                    errors = sectionStats.sumOf { it.errors1w },
                    windows = headerWindows,
                    scaleMax = sectionScaleMax,
                    // A combined chart for a single-endpoint section just duplicates that row.
                    showCombined = sectionStats.size > 1
                )
            )
            for (stat in sectionStats.sortedByDescending { it.count1w }) {
                items.add(
                    ApiStatListItem.Row(
                        section = stat.section,
                        endpoint = stat.endpoint,
                        windows = rowWindows(stat),
                        ttfbMs = stat.avgTtfb1w,
                        maxMs = stat.max1w,
                        errors = stat.errors1w,
                        scaleMax = sectionScaleMax
                    )
                )
            }
        }
        return items
    }

    private fun rowWindows(s: ApiCallWindowStat): List<WindowValue> = listOf(
        WindowValue("15m", s.count15m, s.avg15m),
        WindowValue("1h", s.count1h, s.avg1h),
        WindowValue("1d", s.count1d, s.avg1d),
        WindowValue("1w", s.count1w, s.avg1w)
    )

    private fun aggregateWindows(stats: List<ApiCallWindowStat>): List<WindowValue> {
        fun agg(
            label: String,
            countSel: (ApiCallWindowStat) -> Int,
            avgSel: (ApiCallWindowStat) -> Double?
        ): WindowValue {
            var count = 0
            var weightedSum = 0.0
            for (s in stats) {
                val c = countSel(s)
                val a = avgSel(s)
                if (c > 0 && a != null) {
                    count += c
                    weightedSum += a * c
                }
            }
            return WindowValue(label, count, if (count > 0) weightedSum / count else null)
        }
        return listOf(
            agg("15m", { it.count15m }, { it.avg15m }),
            agg("1h", { it.count1h }, { it.avg1h }),
            agg("1d", { it.count1d }, { it.avg1d }),
            agg("1w", { it.count1w }, { it.avg1w })
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.api_statistics_activity, menu)
        menu.findItem(R.id.action_toggle_monitoring_api_statistics_activity)?.isChecked =
            apiCallTracker.isEnabled
        applyMenuItemTheme(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_refresh_api_statistics_activity -> {
                loadStats()
                return true
            }

            R.id.action_toggle_monitoring_api_statistics_activity -> {
                val newValue = !apiCallTracker.isEnabled
                apiCallTracker.setEnabled(newValue)
                item.isChecked = newValue
                return true
            }

            R.id.action_clear_api_statistics_activity -> {
                executor.execute {
                    redditDataRoomDatabase.apiCallRecordDao().deleteAll()
                    handler.post { loadStats() }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getDefaultSharedPreferences(): SharedPreferences = sharedPreferences

    override fun getCurrentAccountSharedPreferences(): SharedPreferences =
        mCurrentAccountSharedPreferences

    override fun getCustomThemeWrapper(): CustomThemeWrapper = mCustomThemeWrapper

    override fun applyCustomTheme() {
        binding.root.setBackgroundColor(mCustomThemeWrapper.backgroundColor)
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(
            binding.appbarLayoutApiStatisticsActivity,
            binding.collapsingToolbarLayoutApiStatisticsActivity,
            binding.toolbarApiStatisticsActivity
        )
        binding.emptyTextViewApiStatisticsActivity.setTextColor(mCustomThemeWrapper.secondaryTextColor)
    }
}
