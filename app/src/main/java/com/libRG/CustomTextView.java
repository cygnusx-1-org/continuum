/*
 * Copyright (C) 2017 Rajagopal R
 * https://github.com/Rajagopalr3/CustomizedTextView
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Vendored into Continuum and migrated to AndroidX. Trimmed to the rounded-shape
 * background feature (the only part this app uses); the original library's read-more,
 * checkable and custom-font features were removed.
 */
package com.libRG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import androidx.annotation.ColorInt;
import androidx.appcompat.widget.AppCompatTextView;
import ml.docilealligator.infinityforreddit.R;

/**
 * A TextView that can draw a rounded (or oval) shaped background with a configurable border.
 */
public class CustomTextView extends AppCompatTextView {

    private int mBackgroundColor, mBorderColor;
    private int padding, paddingLeft, paddingTop, paddingRight, paddingBottom;
    private int shape;
    private boolean isBorderView;
    private float radius;
    private float strokeWidth;

    public CustomTextView(Context context) {
        this(context, null);
    }

    public CustomTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    @SuppressLint("ResourceType")
    private void init(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CustomTextView);
        isBorderView = a.getBoolean(R.styleable.CustomTextView_lib_setRoundedView, false);
        mBorderColor = a.getColor(R.styleable.CustomTextView_lib_setRoundedBorderColor, Color.parseColor("#B6B6B6"));
        padding = a.getDimensionPixelSize(R.styleable.CustomTextView_android_padding, -1);
        paddingLeft = a.getDimensionPixelSize(R.styleable.CustomTextView_android_paddingLeft, 5);
        paddingTop = a.getDimensionPixelSize(R.styleable.CustomTextView_android_paddingTop, 5);
        paddingRight = a.getDimensionPixelSize(R.styleable.CustomTextView_android_paddingRight, 5);
        paddingBottom = a.getDimensionPixelSize(R.styleable.CustomTextView_android_paddingBottom, 5);
        radius = a.getDimension(R.styleable.CustomTextView_lib_setRadius, 1);
        mBackgroundColor = a.getColor(R.styleable.CustomTextView_lib_setRoundedBGColor, Color.TRANSPARENT);
        strokeWidth = a.getDimension(R.styleable.CustomTextView_lib_setStrokeWidth, 1);
        shape = a.getInt(R.styleable.CustomTextView_lib_setShape, 0);
        a.recycle();
        if (isBorderView) {
            if (padding != -1) {
                setPadding(padding, padding, padding, padding);
            } else {
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            }
        }
        applyShapeBackground();
    }

    private void applyShapeBackground() {
        if (isBorderView) {
            setBackground(getShapeBackground(mBorderColor));
        }
    }

    @SuppressLint("WrongConstant")
    private Drawable getShapeBackground(@ColorInt int color) {
        int cornerRadius;
        if (this.shape == GradientDrawable.OVAL) {
            cornerRadius = (Math.max(this.getHeight(), this.getWidth())) / 2;
        } else {
            cornerRadius = (int) this.radius;
        }
        GradientDrawable shapeDrawable = new GradientDrawable();
        shapeDrawable.setShape(this.shape);
        shapeDrawable.setCornerRadius(cornerRadius);
        shapeDrawable.setColor(mBackgroundColor);
        shapeDrawable.setStroke((int) strokeWidth, color);
        return shapeDrawable;
    }

    @Override
    public void setBackgroundColor(int backgroundColor) {
        this.mBackgroundColor = backgroundColor;
        applyShapeBackground();
    }

    public void setBorderColor(int borderColor) {
        this.mBorderColor = borderColor;
        applyShapeBackground();
    }
}
