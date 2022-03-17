/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.display.darkmode;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.widget.LayoutPreference;

import java.time.LocalTime;

/**
 * Controller for activate/deactivate night mode button
 */
public class DarkModeActivationPreferenceController extends BasePreferenceController {

    private final UiModeManager mUiModeManager;
    private final MetricsFeatureProvider mMetricsFeatureProvider;
    private PowerManager mPowerManager;
    private Button mTurnOffButton;
    private Button mTurnOnButton;
    private TimeFormatter mFormat;
    private LayoutPreference mPreference;

    public DarkModeActivationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mPowerManager = context.getSystemService(PowerManager.class);
        mUiModeManager = context.getSystemService(UiModeManager.class);
        mFormat = new TimeFormatter(context);
        mMetricsFeatureProvider = FeatureFactory.getFactory(context).getMetricsFeatureProvider();
    }

    public DarkModeActivationPreferenceController(Context context, String preferenceKey,
            TimeFormatter f) {
        this(context, preferenceKey);
        mFormat = f;
    }

    @Override
    public final void updateState(Preference preference) {

        final boolean batterySaver = mPowerManager.isPowerSaveMode();
        if (batterySaver) {
            mTurnOnButton.setVisibility(View.GONE);
            mTurnOffButton.setVisibility(View.GONE);
            return;
        }

        final boolean active = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_YES) != 0;
        updateNightMode(active);
    }

    private void updateNightMode(boolean active) {
        final int mode = mUiModeManager.getNightMode();
        String buttonText;

        if (mode == UiModeManager.MODE_NIGHT_AUTO) {
            buttonText = mContext.getString(active
                    ? R.string.dark_ui_activation_off_auto
                    : R.string.dark_ui_activation_on_auto);
        } else if (mode == UiModeManager.MODE_NIGHT_CUSTOM) {
            final LocalTime time = active
                    ? mUiModeManager.getCustomNightModeStart()
                    : mUiModeManager.getCustomNightModeEnd();
            final String timeStr = mFormat.of(time);
            buttonText = mContext.getString(active
                    ? R.string.dark_ui_activation_off_custom
                    : R.string.dark_ui_activation_on_custom, timeStr);
        } else {
            buttonText = mContext.getString(active
                    ? R.string.dark_ui_activation_off_manual
                    : R.string.dark_ui_activation_on_manual);
        }
        if (active) {
            mTurnOnButton.setVisibility(View.GONE);
            mTurnOffButton.setVisibility(View.VISIBLE);
            mTurnOffButton.setText(buttonText);
        } else {
            mTurnOnButton.setVisibility(View.VISIBLE);
            mTurnOffButton.setVisibility(View.GONE);
            mTurnOnButton.setText(buttonText);
        }
    }

    @Override
    public CharSequence getSummary() {
        final boolean isActivated = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_YES) != 0;
        final int mode = mUiModeManager.getNightMode();
        if (mode == UiModeManager.MODE_NIGHT_AUTO) {
            return mContext.getString(isActivated
                    ? R.string.dark_ui_summary_on_auto_mode_auto
                    : R.string.dark_ui_summary_off_auto_mode_auto);
        } else if (mode == UiModeManager.MODE_NIGHT_CUSTOM) {
            final LocalTime time = isActivated
                    ? mUiModeManager.getCustomNightModeEnd()
                    : mUiModeManager.getCustomNightModeStart();
            final String timeStr = mFormat.of(time);

            return mContext.getString(isActivated
                    ? R.string.dark_ui_summary_on_auto_mode_custom
                    : R.string.dark_ui_summary_off_auto_mode_custom, timeStr);
        } else {
            return mContext.getString(isActivated
                    ? R.string.dark_ui_summary_on_auto_mode_never
                    : R.string.dark_ui_summary_off_auto_mode_never);
        }
    }

    private final View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mMetricsFeatureProvider.logClickedPreference(mPreference, getMetricsCategory());
            final boolean active = (mContext.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_YES) != 0;
            mUiModeManager.setNightModeActivated(!active);
            updateNightMode(!active);
        }
    };

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPreference = screen.findPreference(getPreferenceKey());
        mTurnOnButton = mPreference.findViewById(R.id.dark_ui_turn_on_button);
        mTurnOnButton.setOnClickListener(mListener);
        mTurnOffButton = mPreference.findViewById(R.id.dark_ui_turn_off_button);
        mTurnOffButton.setOnClickListener(mListener);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE_UNSEARCHABLE;
    }
}
