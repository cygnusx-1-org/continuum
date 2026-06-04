package ml.docilealligator.infinityforreddit.adapters

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customviews.BarChartView
import ml.docilealligator.infinityforreddit.databinding.ItemApiStatisticsHeaderBinding
import ml.docilealligator.infinityforreddit.databinding.ItemApiStatisticsRowBinding
import kotlin.math.roundToInt

/** One time window's average response time and call count. avgMs is null when the window is empty. */
data class WindowValue(val label: String, val count: Int, val avgMs: Double?)

sealed class ApiStatListItem {
    /** [scaleMax] is the value mapped to full bar height; shared across a section so its rows are comparable. */
    data class Header(
        val section: String,
        val totalCount: Int,
        val errors: Int,
        val windows: List<WindowValue>,
        val scaleMax: Double,
        val showCombined: Boolean
    ) : ApiStatListItem()

    data class Row(
        val section: String,
        val endpoint: String,
        val windows: List<WindowValue>,
        val ttfbMs: Double?,
        val maxMs: Long,
        val errors: Int,
        val scaleMax: Double
    ) : ApiStatListItem()
}

class ApiStatisticsRecyclerViewAdapter(
    private val context: Context,
    private val customThemeWrapper: CustomThemeWrapper
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var allItems: List<ApiStatListItem> = emptyList()
    private var visibleItems: List<ApiStatListItem> = emptyList()
    private var showGood = true
    private var showMarginal = true
    private var showBad = true

    fun setItems(newItems: List<ApiStatListItem>) {
        allItems = newItems
        applyFilter()
    }

    /**
     * Filter rows by colour. Unchecking marginal/bad hides rows containing that colour; unchecking
     * good hides rows that are entirely good (no marginal or bad bars), i.e. shows only problem rows.
     */
    fun setFilter(showGood: Boolean, showMarginal: Boolean, showBad: Boolean) {
        this.showGood = showGood
        this.showMarginal = showMarginal
        this.showBad = showBad
        applyFilter()
    }

    private fun applyFilter() {
        val result = ArrayList<ApiStatListItem>()
        var i = 0
        while (i < allItems.size) {
            val item = allItems[i]
            if (item is ApiStatListItem.Header) {
                // Collect this section's rows (up to the next header) and keep visible ones.
                val visibleRows = ArrayList<ApiStatListItem.Row>()
                var j = i + 1
                while (j < allItems.size && allItems[j] is ApiStatListItem.Row) {
                    val row = allItems[j] as ApiStatListItem.Row
                    if (rowVisible(row)) {
                        visibleRows.add(row)
                    }
                    j++
                }
                // Keep the section header/combined chart if any row survives, or if the combined
                // chart itself shows a qualifying problem under the current filters.
                if (visibleRows.isNotEmpty() || headerVisible(item)) {
                    result.add(item)
                    result.addAll(visibleRows)
                }
                i = j
            } else {
                if (item is ApiStatListItem.Row && rowVisible(item)) {
                    result.add(item)
                }
                i++
            }
        }
        visibleItems = result
        notifyDataSetChanged()
    }

    private fun rowVisible(row: ApiStatListItem.Row): Boolean = passesFilter(row.windows)

    /** Only relevant when the section has a combined chart; judged the same way as a row. */
    private fun headerVisible(header: ApiStatListItem.Header): Boolean =
        header.showCombined && passesFilter(header.windows)

    private fun passesFilter(windows: List<WindowValue>): Boolean {
        val hasMarginal = windowsContain(windows, COLOR_MARGINAL)
        val hasBad = windowsContain(windows, COLOR_BAD)
        if (!showMarginal && hasMarginal) {
            return false
        }
        if (!showBad && hasBad) {
            return false
        }
        // Entirely good (no marginal or bad bars) — hidden when Good is unchecked.
        if (!showGood && !hasMarginal && !hasBad) {
            return false
        }
        return true
    }

    private fun windowsContain(windows: List<WindowValue>, color: Int): Boolean {
        val baseline = windows.lastOrNull()?.avgMs
        return windows.any { w ->
            w.count > 0 && w.avgMs != null && qualityColor(w.avgMs, baseline) == color
        }
    }

    override fun getItemCount(): Int = visibleItems.size

    override fun getItemViewType(position: Int): Int = when (visibleItems[position]) {
        is ApiStatListItem.Header -> VIEW_TYPE_HEADER
        is ApiStatListItem.Row -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemApiStatisticsHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowViewHolder(ItemApiStatisticsRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = visibleItems[position]) {
            is ApiStatListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ApiStatListItem.Row -> (holder as RowViewHolder).bind(item)
        }
    }

    /**
     * Builds the chart bars, colouring each time period green / yellow / red by how its average
     * response time compares to the endpoint's own weekly baseline (the rightmost bar). This shows,
     * per period, whether things are good, marginally worse, or much worse than usual.
     */
    private fun buildBars(windows: List<WindowValue>): List<BarChartView.Bar> {
        val baseline = windows.lastOrNull()?.avgMs
        return windows.map { w ->
            val available = w.count > 0 && w.avgMs != null
            BarChartView.Bar(
                label = w.label,
                value = w.avgMs ?: 0.0,
                valueText = if (available) formatMs(w.avgMs!!) else "",
                available = available,
                color = if (available) qualityColor(w.avgMs!!, baseline) else customThemeWrapper.dividerColor
            )
        }
    }

    /** green when at/near the weekly baseline, yellow when moderately above, red when well above. */
    private fun qualityColor(value: Double, baseline: Double?): Int {
        if (baseline == null || baseline <= 0.0) {
            return COLOR_GOOD
        }
        val ratio = value / baseline
        return when {
            ratio <= MARGINAL_RATIO -> COLOR_GOOD
            ratio <= BAD_RATIO -> COLOR_MARGINAL
            else -> COLOR_BAD
        }
    }

    private inner class HeaderViewHolder(private val binding: ItemApiStatisticsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ApiStatListItem.Header) {
            binding.sectionNameItemApiStatisticsHeader.text = header.section
            binding.sectionNameItemApiStatisticsHeader.setTextColor(customThemeWrapper.colorAccent)

            val errorPart = if (header.errors > 0) {
                context.getString(R.string.api_statistics_errors_suffix, header.errors)
            } else {
                ""
            }
            binding.sectionSummaryItemApiStatisticsHeader.text = context.getString(
                R.string.api_statistics_section_summary, header.totalCount
            ) + errorPart
            binding.sectionSummaryItemApiStatisticsHeader.setTextColor(customThemeWrapper.secondaryTextColor)

            if (headerVisible(header)) {
                binding.combinedTitleItemApiStatisticsHeader.visibility = android.view.View.VISIBLE
                binding.chartItemApiStatisticsHeader.visibility = android.view.View.VISIBLE
                binding.combinedTitleItemApiStatisticsHeader.setTextColor(customThemeWrapper.primaryTextColor)
                binding.chartItemApiStatisticsHeader.setBars(
                    buildBars(header.windows),
                    header.scaleMax,
                    customThemeWrapper.secondaryTextColor,
                    customThemeWrapper.secondaryTextColor
                )
            } else {
                binding.combinedTitleItemApiStatisticsHeader.visibility = android.view.View.GONE
                binding.chartItemApiStatisticsHeader.visibility = android.view.View.GONE
            }
        }
    }

    private inner class RowViewHolder(private val binding: ItemApiStatisticsRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ApiStatListItem.Row) {
            binding.endpointItemApiStatisticsRow.text = row.endpoint
            binding.endpointItemApiStatisticsRow.setTextColor(customThemeWrapper.primaryTextColor)

            binding.chartItemApiStatisticsRow.setBars(
                buildBars(row.windows),
                row.scaleMax,
                customThemeWrapper.secondaryTextColor,
                customThemeWrapper.secondaryTextColor
            )

            val counts = row.windows.joinToString(" · ") { "${it.label} ${it.count}" }
            val ttfb = if (row.ttfbMs != null && row.ttfbMs > 0) {
                context.getString(R.string.api_statistics_ttfb_part, formatMs(row.ttfbMs))
            } else {
                ""
            }
            val errors = if (row.errors > 0) {
                context.getString(R.string.api_statistics_errors_suffix, row.errors)
            } else {
                ""
            }
            val recent = row.windows.firstOrNull()?.avgMs
            val baseline = row.windows.lastOrNull()?.avgMs
            val slower = if (recent != null && baseline != null && baseline > 0 &&
                recent > baseline * SLOW_FACTOR
            ) {
                context.getString(R.string.api_statistics_slower_now)
            } else {
                ""
            }
            binding.detailItemApiStatisticsRow.text = context.getString(
                R.string.api_statistics_detail_line, counts, ttfb, formatMs(row.maxMs.toDouble())
            ) + errors + slower
            binding.detailItemApiStatisticsRow.setTextColor(customThemeWrapper.secondaryTextColor)
        }
    }

    private fun formatMs(ms: Double): String =
        if (ms >= 1000) String.format("%.1f s", ms / 1000.0) else "${ms.roundToInt()} ms"

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ROW = 1

        // A period is "good" up to MARGINAL_RATIO of the weekly baseline, "marginal" up to
        // BAD_RATIO, and "bad" beyond that. SLOW_FACTOR matches the "bad" threshold for the
        // textual "slower now" hint.
        private const val MARGINAL_RATIO = 1.25
        private const val BAD_RATIO = 1.5
        private const val SLOW_FACTOR = BAD_RATIO

        // Traffic-light colours that read on both light and dark themes.
        private val COLOR_GOOD = Color.parseColor("#43A047")     // green
        private val COLOR_MARGINAL = Color.parseColor("#F9A825") // amber/yellow
        private val COLOR_BAD = Color.parseColor("#E53935")      // red
    }
}
