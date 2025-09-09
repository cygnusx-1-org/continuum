package ml.docilealligator.infinityforreddit.customviews.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import ml.docilealligator.infinityforreddit.CustomFontReceiver;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapperReceiver;

public class CollapsiblePreferenceCategory extends CustomFontPreferenceCategory {

    private boolean collapsed = true;
    private ImageView collapseIndicator;

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CollapsiblePreferenceCategory(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View titleView = holder.findViewById(android.R.id.title);
        if (titleView instanceof TextView textView) {
            int drawableRes = collapsed
                    ? R.drawable.ic_baseline_arrow_drop_down_24dp
                    : R.drawable.ic_baseline_arrow_drop_up_24dp;

            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawableRes, 0);
            textView.setCompoundDrawablePadding(16);
        }

        holder.itemView.setOnClickListener(v -> {
            collapsed = !collapsed;
            setChildrenVisible(!collapsed);

            if (titleView instanceof TextView tv) {
                int drawable = collapsed
                        ? R.drawable.ic_baseline_arrow_drop_down_24dp
                        : R.drawable.ic_baseline_arrow_drop_up_24dp;
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawable, 0);
            }

            notifyChanged();
        });

        setChildrenVisible(!collapsed);
    }


    private void updateArrow() {
        if (collapseIndicator != null) {
            if (collapsed) {
                collapseIndicator.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24dp);
            } else {
                collapseIndicator.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24dp);
            }
        }
    }

    private void setChildrenVisible(boolean visible) {
        int count = getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference p = getPreference(i);
            p.setVisible(visible);

            if (p instanceof CustomFontSwitchPreference switchPref) {
                switchPref.setCustomFont(typeface, null, null);
                switchPref.setCustomThemeWrapper(customThemeWrapper);
            }
        }
    }




    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        setChildrenVisible(!collapsed);
        updateArrow();
        notifyChanged();
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
