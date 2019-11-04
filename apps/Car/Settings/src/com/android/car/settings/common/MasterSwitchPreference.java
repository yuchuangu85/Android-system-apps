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

package com.android.car.settings.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.android.car.settings.R;

/** A preference that can be clicked on one side and toggled on another. */
public class MasterSwitchPreference extends TwoActionPreference {

    /**
     * Interface definition for a callback to be invoked when the switch is toggled.
     */
    public interface OnSwitchToggleListener {
        /**
         * Called when a switch was toggled.
         *
         * @param preference the preference whose switch was toggled.
         * @param isChecked  the new state of the switch.
         */
        void onToggle(MasterSwitchPreference preference, boolean isChecked);
    }

    private Switch mSwitch;
    private boolean mIsChecked;
    private OnSwitchToggleListener mToggleListener;

    private final CompoundButton.OnCheckedChangeListener mCheckedChangeListener =
            (buttonView, isChecked) -> {
                if (mToggleListener != null) {
                    mToggleListener.onToggle(this, isChecked);
                }
            };

    public MasterSwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public MasterSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MasterSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MasterSwitchPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.master_switch_widget);
    }

    /** Sets the listener that handles the change in switch state. */
    public void setSwitchToggleListener(OnSwitchToggleListener listener) {
        mToggleListener = listener;
    }

    /** Gets the listener that handles the change in switch state. */
    public OnSwitchToggleListener getSwitchToggleListener() {
        return mToggleListener;
    }

    @Override
    protected void onBindWidgetFrame(View widgetFrame) {
        mSwitch = widgetFrame.findViewById(R.id.master_switch);
        mSwitch.setChecked(mIsChecked);
        mSwitch.setOnCheckedChangeListener(mCheckedChangeListener);
        widgetFrame.setOnClickListener(v -> setSwitchChecked(!mIsChecked));
    }

    /**
     * Sets the state of the switch. Can be set even when it isn't visible or bound in order to set
     * the initial state.
     */
    public void setSwitchChecked(boolean checked) {
        mIsChecked = checked;
        if (!isActionShown()) {
            return;
        }

        if (mSwitch != null) {
            mSwitch.setChecked(checked);
        }
    }

    /** Gets the state of the switch. */
    public boolean isSwitchChecked() {
        return mIsChecked;
    }
}
