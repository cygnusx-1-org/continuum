package ml.docilealligator.infinityforreddit.customviews

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper

/**
 * Applies the user's custom theme to chips, which Material only styles from theme attributes the
 * app does not drive.
 *
 * Shared by the Customize Post Filter screen and its add-rule sheet so the chips in both are
 * measurably identical rather than merely similar.
 */
class FilterChipStyler(customThemeWrapper: CustomThemeWrapper, private val typeface: Typeface?) {

    private val primaryTextColor = customThemeWrapper.primaryTextColor
    private val primaryIconColor = customThemeWrapper.primaryIconColor
    private val backgroundColor = customThemeWrapper.backgroundColor
    private val dividerColor = customThemeWrapper.dividerColor
    private val accentColor = customThemeWrapper.colorAccent

    // Chips sit on a filled card, so their unchecked fill is the screen background rather than the
    // card colour, and their checked fill is the accent the rest of the app uses for selected chips.
    private val backgroundColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(accentColor, backgroundColor)
    )
    private val strokeColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(accentColor, dividerColor)
    )
    private val textColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(customThemeWrapper.chipTextColor, primaryTextColor)
    )

    /**
     * Chips are inflated rather than constructed: `Chip(context)` only picks up the theme's default
     * chipStyle, which is not the filter / input / assist style each call site needs.
     */
    fun inflate(parent: ViewGroup, @LayoutRes layoutRes: Int): Chip {
        val chip = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false) as Chip
        style(chip)
        return chip
    }

    fun style(chip: Chip) {
        chip.setTextColor(if (chip.isCheckable) textColors else ColorStateList.valueOf(primaryTextColor))
        chip.chipBackgroundColor =
            if (chip.isCheckable) backgroundColors else ColorStateList.valueOf(backgroundColor)
        chip.chipStrokeColor =
            if (chip.isCheckable) strokeColors else ColorStateList.valueOf(dividerColor)
        chip.chipStrokeWidth = chip.resources.displayMetrics.density
        chip.closeIconTint = ColorStateList.valueOf(primaryIconColor)
        chip.chipIconTint = ColorStateList.valueOf(primaryIconColor)
        typeface?.let { chip.typeface = it }
    }

    fun styleAll(chipGroup: ChipGroup) {
        for (index in 0 until chipGroup.childCount) {
            (chipGroup.getChildAt(index) as? Chip)?.let { style(it) }
        }
    }
}
