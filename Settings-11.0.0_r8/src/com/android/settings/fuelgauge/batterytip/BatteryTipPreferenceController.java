/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.fuelgauge.batterytip;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.SettingsActivity;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.InstrumentedPreferenceFragment;
import com.android.settings.fuelgauge.batterytip.actions.BatteryTipAction;
import com.android.settings.fuelgauge.batterytip.tips.BatteryTip;
import com.android.settings.fuelgauge.batterytip.tips.SummaryTip;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.widget.CardPreference;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.fuelgauge.EstimateKt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller in charge of the battery tip group
 */
public class BatteryTipPreferenceController extends BasePreferenceController {

    public static final String PREF_NAME = "battery_tip";

    private static final String TAG = "BatteryTipPreferenceController";
    private static final int REQUEST_ANOMALY_ACTION = 0;
    private static final String KEY_BATTERY_TIPS = "key_battery_tips";

    private BatteryTipListener mBatteryTipListener;
    private List<BatteryTip> mBatteryTips;
    private Map<String, BatteryTip> mBatteryTipMap;
    private SettingsActivity mSettingsActivity;
    private MetricsFeatureProvider mMetricsFeatureProvider;
    private boolean mNeedUpdate;
    @VisibleForTesting
    CardPreference mCardPreference;
    @VisibleForTesting
    Context mPrefContext;
    InstrumentedPreferenceFragment mFragment;

    public BatteryTipPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mBatteryTipMap = new HashMap<>();
        mMetricsFeatureProvider = FeatureFactory.getFactory(context).getMetricsFeatureProvider();
        mNeedUpdate = true;
    }

    public void setActivity(SettingsActivity activity) {
        mSettingsActivity = activity;
    }

    public void setFragment(InstrumentedPreferenceFragment fragment) {
        mFragment = fragment;
    }

    public void setBatteryTipListener(BatteryTipListener lsn) {
        mBatteryTipListener = lsn;
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE_UNSEARCHABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPrefContext = screen.getContext();
        mCardPreference = screen.findPreference(getPreferenceKey());

        // Add summary tip in advance to avoid UI flakiness
        final SummaryTip summaryTip = new SummaryTip(BatteryTip.StateType.NEW,
                EstimateKt.AVERAGE_TIME_TO_DISCHARGE_UNKNOWN);
        summaryTip.updatePreference(mCardPreference);
    }

    public void updateBatteryTips(List<BatteryTip> batteryTips) {
        if (batteryTips == null) {
            return;
        }
        if (mBatteryTips == null) {
            mBatteryTips = batteryTips;
        } else {
            // mBatteryTips and batteryTips always have the same length and same sequence.
            for (int i = 0, size = batteryTips.size(); i < size; i++) {
                mBatteryTips.get(i).updateState(batteryTips.get(i));
            }
        }

        for (int i = 0, size = batteryTips.size(); i < size; i++) {
            final BatteryTip batteryTip = mBatteryTips.get(i);
            batteryTip.sanityCheck(mContext);
            if (batteryTip.getState() != BatteryTip.StateType.INVISIBLE) {
                batteryTip.updatePreference(mCardPreference);
                mBatteryTipMap.put(mCardPreference.getKey(), batteryTip);
                batteryTip.log(mContext, mMetricsFeatureProvider);
                mNeedUpdate = batteryTip.needUpdate();
                break;
            }
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        final BatteryTip batteryTip = mBatteryTipMap.get(preference.getKey());
        if (batteryTip != null) {
            if (batteryTip.shouldShowDialog()) {
                BatteryTipDialogFragment dialogFragment = BatteryTipDialogFragment.newInstance(
                        batteryTip, mFragment.getMetricsCategory());
                dialogFragment.setTargetFragment(mFragment, REQUEST_ANOMALY_ACTION);
                dialogFragment.show(mFragment.getFragmentManager(), TAG);
            } else {
                final BatteryTipAction action = BatteryTipUtils.getActionForBatteryTip(batteryTip,
                        mSettingsActivity, mFragment);
                if (action != null) {
                    action.handlePositiveAction(mFragment.getMetricsCategory());
                }
                if (mBatteryTipListener != null) {
                    mBatteryTipListener.onBatteryTipHandled(batteryTip);
                }
            }

            return true;
        }

        return super.handlePreferenceTreeClick(preference);
    }

    public void restoreInstanceState(Bundle bundle) {
        if (bundle != null) {
            List<BatteryTip> batteryTips = bundle.getParcelableArrayList(KEY_BATTERY_TIPS);
            updateBatteryTips(batteryTips);
        }
    }

    public void saveInstanceState(Bundle outState) {
        outState.putParcelableList(KEY_BATTERY_TIPS, mBatteryTips);
    }

    public boolean needUpdate() {
        return mNeedUpdate;
    }

    /**
     * Listener to give the control back to target fragment
     */
    public interface BatteryTipListener {
        /**
         * This method is invoked once battery tip is handled, then target fragment could do
         * extra work.
         *
         * @param batteryTip that has been handled
         */
        void onBatteryTipHandled(BatteryTip batteryTip);
    }
}
