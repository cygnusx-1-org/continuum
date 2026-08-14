package ml.docilealligator.infinityforreddit.customviews.preference

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.utils.deriveContrastingColor

class SliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var min: Int
    var max: Int
    var stepSize: Int
    var defaultValue: Int = 0
    private val customThemeWrapper: CustomThemeWrapper?
    private val typeface: Typeface?

    init {
        layoutResource = R.layout.preference_slider
        val app = context.applicationContext
        if (app is Infinity) {
            customThemeWrapper = app.customThemeWrapper
            typeface = app.typeface
        } else {
            customThemeWrapper = null
            typeface = null
        }

        summaryProvider = SummaryProvider<SliderPreference> { preference ->
            preference.getPersistedInt(defaultValue).toString()
        }

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SliderPreference,
            0, 0
        ).let {
            try {
                min = it.getInt(R.styleable.SliderPreference_sliderMin, 0)
                max = it.getInt(R.styleable.SliderPreference_sliderMax, 100)
                stepSize = it.getInt(R.styleable.SliderPreference_sliderStepSize, 1)
            } finally {
                it.recycle()
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val slider = holder.findViewById(R.id.slider_preference_slider) as? Slider
        slider?.apply {
            // onBindViewHolder runs again on every notifyChanged(), including the one the listener
            // below fires, so without clearing first the listeners pile up on the recycled view and
            // each drag step persists once per accumulated listener. Clear before assigning value,
            // since Slider dispatches its change listeners for programmatic sets too.
            clearOnChangeListeners()
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = this@SliderPreference.stepSize.toFloat()
            // Slider throws if the value falls outside [valueFrom, valueTo], so never hand it a
            // stored value straight through — an install predating a change to sliderMin/sliderMax
            // still carries the old range.
            value = getPersistedInt(defaultValue).coerceIn(min, max).toFloat()

            addOnChangeListener { _, newValue, _ ->
                persistInt(newValue.toInt())
                notifyChanged()
            }
        }

        val iconImageView = holder.findViewById(android.R.id.icon)
        val titleTextView = holder.findViewById(android.R.id.title)
        val summaryTextView = holder.findViewById(android.R.id.summary)

        customThemeWrapper?.let {
            if (iconImageView is ImageView) {
                if (isEnabled) {
                    iconImageView.setColorFilter(
                        it.primaryIconColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    iconImageView.setColorFilter(
                        it.secondaryTextColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
            if (titleTextView is TextView) {
                titleTextView.setTextColor(it.primaryTextColor)
            }
            if (summaryTextView is TextView) {
                summaryTextView.setTextColor(it.secondaryTextColor)
            }

            slider?.thumbTintList = ColorStateList.valueOf(it.colorAccent)
            slider?.trackActiveTintList = ColorStateList.valueOf(it.colorAccent)
            slider?.trackInactiveTintList = ColorStateList.valueOf(deriveContrastingColor(it.colorAccent))
        }

        if (typeface != null) {
            if (titleTextView is TextView) {
                titleTextView.setTypeface(typeface)
            }
            if (summaryTextView is TextView) {
                summaryTextView.setTypeface(typeface)
            }
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        if (defaultValue is Int) {
            this.defaultValue = defaultValue
        }
        // Write the value through on first initialisation, the way the stock AndroidX preferences
        // do. Without this the key stays absent from SharedPreferences until the slider is dragged,
        // so "fresh install" and "explicitly set to the default" are different states on disk, and
        // any reader whose getInt() fallback differs from app:defaultValue sees a different value in
        // each. A value outside the slider's range was never chosen by the user, so heal it back to
        // the declared default rather than clamping to an edge.
        val stored = getPersistedInt(this.defaultValue)
        persistInt(if (stored in min..max) stored else this.defaultValue)
        notifyChanged()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        // Capture app:defaultValue here rather than only in onSetInitialValue: this runs whenever
        // the preference is inflated, whereas onSetInitialValue is handed the default only when the
        // key is absent and is handed null once it exists. Relying on the latter left defaultValue
        // at 0 for every install that had already stored a value.
        defaultValue = a.getInt(index, 0)
        return defaultValue
    }

    fun setSummaryTemplate(stringResId: Int) {
        summaryProvider = SummaryProvider<SliderPreference> { preference ->
            context.getString(
                stringResId,
                preference.getPersistedInt(defaultValue))
        }
    }
}