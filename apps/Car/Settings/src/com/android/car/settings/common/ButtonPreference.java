/*
 * Copyright 2018 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;

/**
 * {@link Preference} with a secondary clickable button on the side.
 * {@link #setLayoutResource(int)} or the {@code widgetLayout} resource may be used to specify
 * the icon to display in the button.
 *
 * <p>Note: the button is enabled even when {@link #isEnabled()} is {@code false}.
 */
public class ButtonPreference extends TwoActionPreference {

    /**
     * Interface definition for a callback to be invoked when the button is clicked.
     */
    public interface OnButtonClickListener {
        /**
         * Called when a button has been clicked.
         *
         * @param preference the preference whose button was clicked.
         */
        void onButtonClick(ButtonPreference preference);
    }

    private OnButtonClickListener mOnButtonClickListener;

    public ButtonPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ButtonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonPreference(Context context) {
        super(context);
    }

    /**
     * Sets an {@link OnButtonClickListener} to be invoked when the button is clicked.
     */
    public void setOnButtonClickListener(OnButtonClickListener listener) {
        mOnButtonClickListener = listener;
    }

    /** Virtually clicks the button contained inside this preference. */
    public void performButtonClick() {
        if (isActionShown()) {
            if (mOnButtonClickListener != null) {
                mOnButtonClickListener.onButtonClick(this);
            }
        }
    }

    @Override
    protected void onBindWidgetFrame(View widgetFrame) {
        widgetFrame.setOnClickListener(v -> performButtonClick());
    }
}
