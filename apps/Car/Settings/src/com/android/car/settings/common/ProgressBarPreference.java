/*
 * Copyright (C) 2019 The Android Open Source Project
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
 */

package com.android.car.settings.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;

/**
 * Preference which shows a progress bar. The progress bar layout shown can be changed by setting
 * the xml layout attribute. The canonical example can be seen in
 * {@link R.layout#progress_bar_preference}.
 */
public class ProgressBarPreference extends Preference {

    private CharSequence mMinLabel;
    private CharSequence mMaxLabel;

    private int mMin;
    private int mMax;
    private int mProgress;

    public ProgressBarPreference(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public ProgressBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public ProgressBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ProgressBarPreference(Context context) {
        super(context);
        init(/* attrs= */ null);
    }

    private void init(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.ProgressBarPreference);

        mMin = a.getInteger(R.styleable.ProgressBarPreference_min, 0);
        mMax = a.getInteger(R.styleable.ProgressBarPreference_max, 100);
        mProgress = a.getInteger(R.styleable.ProgressBarPreference_progress, 0);
        mMinLabel = a.getString(R.styleable.ProgressBarPreference_minLabel);
        mMaxLabel = a.getString(R.styleable.ProgressBarPreference_maxLabel);

        a.recycle();
    }

    /** Sets the min label of the progress bar. */
    public void setMinLabel(CharSequence startLabel) {
        if (mMinLabel != startLabel) {
            mMinLabel = startLabel;
            notifyChanged();
        }
    }

    /** Sets the max label of the progress bar. */
    public void setMaxLabel(CharSequence endLabel) {
        if (mMaxLabel != endLabel) {
            mMaxLabel = endLabel;
            notifyChanged();
        }
    }

    /** Sets the minimum possible value of the progress bar. */
    public void setMin(int min) {
        if (mMin != min) {
            mMin = min;
            notifyChanged();
        }
    }

    /** Sets the maximum possible value of the progress bar. */
    public void setMax(int max) {
        if (mMax != max) {
            mMax = max;
            notifyChanged();
        }
    }

    /** Sets the current progress value of the progress bar. */
    public void setProgress(int progress) {
        if (mProgress != progress) {
            mProgress = progress;
            notifyChanged();
        }
    }

    /** Returns the current progress value of the progress bar. */
    public int getProgress() {
        return mProgress;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        ProgressBar progressBar = (ProgressBar) view.findViewById(android.R.id.progress);
        progressBar.setMin(mMin);
        progressBar.setMax(mMax);
        progressBar.setProgress(mProgress);

        View progressBarLabels = view.findViewById(R.id.progress_bar_labels);
        if (TextUtils.isEmpty(mMinLabel) && TextUtils.isEmpty(mMaxLabel)) {
            progressBarLabels.setVisibility(View.GONE);
        } else {
            progressBarLabels.setVisibility(View.VISIBLE);

            TextView minLabel = (TextView) view.findViewById(android.R.id.text1);
            if (minLabel != null) {
                minLabel.setText(mMinLabel);
            }
            TextView maxLabel = (TextView) view.findViewById(android.R.id.text2);
            if (maxLabel != null) {
                maxLabel.setText(mMaxLabel);
            }
        }
    }
}
