package ml.docilealligator.infinityforreddit.bottomsheetfragments

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.customviews.FilterChipStyler
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment
import ml.docilealligator.infinityforreddit.databinding.FragmentAddPostFilterRuleBottomSheetBinding
import ml.docilealligator.infinityforreddit.postfilter.FilterRule
import ml.docilealligator.infinityforreddit.postfilter.PostFilterRules
import ml.docilealligator.infinityforreddit.postfilter.RuleField
import ml.docilealligator.infinityforreddit.utils.Utils
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Adds or edits one [FilterRule]: pick a polarity, pick what it matches on, type the term.
 *
 * This is the single entry point that replaced twelve include/exclude text boxes, so it is also
 * where the validation those boxes never did now lives — an unusable regex or a comma that the
 * comma-separated storage would silently split into two terms is refused here, as you type, rather
 * than surfacing as a toast on save or as a filter that quietly matches the wrong thing.
 */
class AddPostFilterRuleBottomSheetFragment : LandscapeExpandedRoundedBottomSheetDialogFragment() {

    /**
     * Implemented by the host activity. An interface rather than a cast to the concrete activity so
     * the sheet stays usable from anywhere that edits a rule list.
     */
    interface Host {
        fun onRuleSubmitted(original: FilterRule?, rule: FilterRule)

        /**
         * The user asked to pick values with the subreddit / user selector instead of typing one.
         * The host takes over: the sheet is already dismissed by the time this is called.
         */
        fun onRuleValuePickerRequested(field: RuleField, exclude: Boolean)
    }

    companion object {
        const val EXTRA_RULE = "ER"
        const val EXTRA_EXISTING_RULES = "EER"
        const val EXTRA_INITIAL_FIELD = "EIF"
        const val EXTRA_INITIAL_EXCLUDE = "EIE"

        private const val SELECTED_FIELD_STATE = "SFS"
        private const val EXCLUDE_STATE = "ES"
    }

    private lateinit var host: Host
    private lateinit var binding: FragmentAddPostFilterRuleBottomSheetBinding
    private var editedRule: FilterRule? = null
    private var existingRules: List<FilterRule> = emptyList()
    private var selectedField = RuleField.SUBREDDIT
    private var exclude = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAddPostFilterRuleBottomSheetBinding.inflate(inflater, container, false)
        val activity = requireActivity() as BaseActivity
        val customThemeWrapper = activity.customThemeWrapper
        val chipStyler = FilterChipStyler(customThemeWrapper, activity.typeface)

        val arguments = requireArguments()
        editedRule = BundleCompat.getParcelable(arguments, EXTRA_RULE, FilterRule::class.java)
        existingRules =
            BundleCompat.getParcelableArrayList(arguments, EXTRA_EXISTING_RULES, FilterRule::class.java)
                ?: emptyList()
        // The chips are rebuilt from these on every onCreateView, so a rotation would otherwise reset
        // the pick back to the argument while the typed value survives in the restored EditText —
        // turning a half-written "Title regex" rule into a Subreddit one on the next OK.
        selectedField = savedInstanceState?.let { RuleField.entries[it.getInt(SELECTED_FIELD_STATE)] }
            ?: editedRule?.field
            ?: RuleField.entries[arguments.getInt(EXTRA_INITIAL_FIELD, RuleField.SUBREDDIT.ordinal)]
        exclude = savedInstanceState?.getBoolean(EXCLUDE_STATE)
            ?: editedRule?.exclude
            ?: arguments.getBoolean(EXTRA_INITIAL_EXCLUDE, true)

        val primaryTextColor = customThemeWrapper.primaryTextColor
        val secondaryTextColor = customThemeWrapper.secondaryTextColor
        binding.titleTextViewAddPostFilterRuleBottomSheetFragment.setText(
            if (editedRule == null) R.string.post_filter_add_rule else R.string.post_filter_edit_rule
        )
        binding.titleTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(primaryTextColor)
        binding.polarityLabelTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(secondaryTextColor)
        binding.polaritySummaryTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(secondaryTextColor)
        binding.fieldLabelTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(secondaryTextColor)
        binding.valueTextInputLayoutAddPostFilterRuleBottomSheetFragment.boxStrokeColor = primaryTextColor
        binding.valueTextInputLayoutAddPostFilterRuleBottomSheetFragment.defaultHintTextColor =
            ColorStateList.valueOf(primaryTextColor)
        binding.valueTextInputEditTextAddPostFilterRuleBottomSheetFragment.setTextColor(primaryTextColor)
        binding.pickImageViewAddPostFilterRuleBottomSheetFragment.setImageDrawable(
            Utils.getTintedDrawable(activity, R.drawable.ic_add_24dp, customThemeWrapper.primaryIconColor)
        )
        binding.cancelTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(primaryTextColor)
        binding.saveTextViewAddPostFilterRuleBottomSheetFragment.setTextColor(customThemeWrapper.colorAccent)

        chipStyler.styleAll(binding.polarityChipGroupAddPostFilterRuleBottomSheetFragment)
        binding.excludeChipAddPostFilterRuleBottomSheetFragment.isChecked = exclude
        binding.includeChipAddPostFilterRuleBottomSheetFragment.isChecked = !exclude
        binding.polarityChipGroupAddPostFilterRuleBottomSheetFragment.setOnCheckedStateChangeListener { _, checkedIds ->
            exclude = checkedIds.firstOrNull() != R.id.include_chip_add_post_filter_rule_bottom_sheet_fragment
            onSelectionChanged()
        }

        val fieldChipGroup = binding.fieldChipGroupAddPostFilterRuleBottomSheetFragment
        for (field in RuleField.entries) {
            val chip = chipStyler.inflate(fieldChipGroup, R.layout.chip_post_filter_field)
            chip.id = View.generateViewId()
            chip.text = getString(fieldLabelRes(field))
            chip.tag = field
            chip.isChecked = field == selectedField
            fieldChipGroup.addView(chip)
        }
        fieldChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checked = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedField = group.findViewById<View>(checked).tag as RuleField
            onSelectionChanged()
        }

        binding.valueTextInputEditTextAddPostFilterRuleBottomSheetFragment.setText(editedRule?.value)
        binding.valueTextInputEditTextAddPostFilterRuleBottomSheetFragment.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    validate()
                }
            }
        )

        binding.pickImageViewAddPostFilterRuleBottomSheetFragment.setOnClickListener {
            val field = selectedField
            val polarity = exclude
            dismiss()
            host.onRuleValuePickerRequested(field, polarity)
        }
        binding.cancelTextViewAddPostFilterRuleBottomSheetFragment.setOnClickListener { dismiss() }
        binding.saveTextViewAddPostFilterRuleBottomSheetFragment.setOnClickListener {
            val value = currentValue()
            if (validate()) {
                dismiss()
                host.onRuleSubmitted(editedRule, FilterRule(selectedField, exclude, value))
            }
        }

        onSelectionChanged()
        activity.typeface?.let { Utils.setFontToAllTextViews(binding.root, it) }
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as Host
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SELECTED_FIELD_STATE, selectedField.ordinal)
        outState.putBoolean(EXCLUDE_STATE, exclude)
    }

    private fun currentValue(): String =
        binding.valueTextInputEditTextAddPostFilterRuleBottomSheetFragment.text?.toString()?.trim().orEmpty()

    private fun onSelectionChanged() {
        binding.valueTextInputEditTextAddPostFilterRuleBottomSheetFragment.hint = getString(hintRes(selectedField))
        binding.polaritySummaryTextViewAddPostFilterRuleBottomSheetFragment.setText(
            if (exclude) R.string.post_filter_rule_exclude_summary else R.string.post_filter_rule_include_summary
        )
        // Only subreddits and users have a picker to bulk-select from.
        binding.pickImageViewAddPostFilterRuleBottomSheetFragment.visibility =
            if (selectedField == RuleField.SUBREDDIT || selectedField == RuleField.USER) View.VISIBLE else View.GONE
        validate()
    }

    /** Returns true when the current input is a rule that can be stored, and shows why when not. */
    private fun validate(): Boolean {
        val value = currentValue()
        val error = when {
            value.isEmpty() -> null
            PostFilterRules.isCommaSeparated(selectedField) && value.contains(',') ->
                getString(R.string.post_filter_rule_error_comma)
            selectedField == RuleField.TITLE_REGEX && !isValidRegex(value) -> getString(R.string.invalid_regex)
            isDuplicate(value) -> getString(R.string.post_filter_rule_error_duplicate)
            else -> null
        }
        binding.valueTextInputLayoutAddPostFilterRuleBottomSheetFragment.error = error
        val valid = value.isNotEmpty() && error == null
        binding.saveTextViewAddPostFilterRuleBottomSheetFragment.isEnabled = valid
        binding.saveTextViewAddPostFilterRuleBottomSheetFragment.alpha = if (valid) 1f else 0.5f
        return valid
    }

    private fun isDuplicate(value: String): Boolean {
        val candidate = FilterRule(selectedField, exclude, value)
        val original = editedRule
        return existingRules.any { it.isSameTermAs(candidate) && (original == null || !it.isSameTermAs(original)) }
    }

    private fun isValidRegex(value: String): Boolean = try {
        Pattern.compile(value)
        true
    } catch (e: PatternSyntaxException) {
        false
    }

    private fun fieldLabelRes(field: RuleField): Int = when (field) {
        RuleField.SUBREDDIT -> R.string.post_filter_field_subreddit
        RuleField.USER -> R.string.post_filter_field_user
        RuleField.FLAIR -> R.string.post_filter_field_flair
        RuleField.DOMAIN -> R.string.post_filter_field_domain
        RuleField.TITLE_KEYWORD -> R.string.post_filter_field_title_keyword
        RuleField.TITLE_REGEX -> R.string.post_filter_field_title_regex
    }

    private fun hintRes(field: RuleField): Int = when (field) {
        RuleField.SUBREDDIT -> R.string.post_filter_rule_hint_subreddit
        RuleField.USER -> R.string.post_filter_rule_hint_user
        RuleField.FLAIR -> R.string.post_filter_rule_hint_flair
        RuleField.DOMAIN -> R.string.post_filter_rule_hint_domain
        RuleField.TITLE_KEYWORD -> R.string.post_filter_rule_hint_title_keyword
        RuleField.TITLE_REGEX -> R.string.post_filter_rule_hint_title_regex
    }
}
