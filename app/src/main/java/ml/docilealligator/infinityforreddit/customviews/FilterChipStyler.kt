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

    // A checked chip is filled the way the subscribe button is, because chipTextColor — the label
    // colour below — is the theme's foreground for exactly that fill and nothing else. Filling with
    // colorAccent instead left the label invisible on any theme with a light accent, which White
    // Dark, White Amoled and Dracula all ship.
    //
    // Of that pair it is `subscribed`, the colour the button wears once you are subscribed, which
    // is the state a checked chip is in. Note the pair tracks the action the button offers rather
    // than the state it is in, so this is the shade a theme picks for undoing something — expect
    // it to be the louder of the two.
    private val selectedColor = customThemeWrapper.subscribed

    // Chips sit on a filled card, so their unchecked fill is the screen background rather than the
    // card colour.
    private val backgroundColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(selectedColor, backgroundColor)
    )
    private val strokeColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(selectedColor, dividerColor)
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
