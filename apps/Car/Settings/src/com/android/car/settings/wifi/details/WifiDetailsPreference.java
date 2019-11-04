/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.wifi.details;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;

/**
 * A Preference to be used with the Wifi Network Detail Fragment that allows a summary text to be
 * set inside the widget resource
 */
public class WifiDetailsPreference extends Preference {
    private String mDetailText;

    public WifiDetailsPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setWidgetLayoutResource(R.layout.summary_preference_widget);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    WifiDetailsPreference(Context context) {
        super(context);
    }

    /**
     * Sets the detail text.
     */
    public void setDetailText(String text) {
        if (TextUtils.equals(mDetailText, text)) {
            return;
        }
        mDetailText = text;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        TextView textView = ((TextView) view.findViewById(R.id.widget_summary));
        textView.setText(mDetailText);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    String getDetailText() {
        return mDetailText;
    }
}
