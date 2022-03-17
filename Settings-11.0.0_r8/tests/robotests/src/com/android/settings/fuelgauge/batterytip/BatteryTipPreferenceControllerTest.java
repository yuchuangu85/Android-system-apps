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

import static com.android.settings.fuelgauge.batterytip.tips.BatteryTip.TipType
        .SMART_BATTERY_MANAGER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.InstrumentedPreferenceFragment;
import com.android.settings.fuelgauge.batterytip.tips.BatteryTip;
import com.android.settings.fuelgauge.batterytip.tips.SummaryTip;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.widget.CardPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class BatteryTipPreferenceControllerTest {

    private static final String KEY_PREF = "battery_tip";
    private static final String KEY_TIP = "key_battery_tip";
    private static final long AVERAGE_TIME_MS = DateUtils.HOUR_IN_MILLIS;

    @Mock
    private BatteryTipPreferenceController.BatteryTipListener mBatteryTipListener;
    @Mock
    private PreferenceScreen mPreferenceScreen;
    @Mock
    private BatteryTip mBatteryTip;
    @Mock
    private SettingsActivity mSettingsActivity;
    @Mock
    private InstrumentedPreferenceFragment mFragment;

    private Context mContext;
    private CardPreference mCardPreference;
    private BatteryTipPreferenceController mBatteryTipPreferenceController;
    private List<BatteryTip> mOldBatteryTips;
    private List<BatteryTip> mNewBatteryTips;
    private FakeFeatureFactory mFeatureFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mCardPreference = spy(new CardPreference(mContext));
        when(mPreferenceScreen.getContext()).thenReturn(mContext);
        doReturn(mCardPreference).when(mPreferenceScreen).findPreference(KEY_PREF);
        mFeatureFactory = FakeFeatureFactory.setupForTest();

        mOldBatteryTips = new ArrayList<>();
        mOldBatteryTips.add(new SummaryTip(BatteryTip.StateType.NEW, AVERAGE_TIME_MS));
        mNewBatteryTips = new ArrayList<>();
        mNewBatteryTips.add(new SummaryTip(BatteryTip.StateType.INVISIBLE, AVERAGE_TIME_MS));

        mBatteryTipPreferenceController = buildBatteryTipPreferenceController();
        mBatteryTipPreferenceController.mCardPreference = mCardPreference;
        mBatteryTipPreferenceController.mPrefContext = mContext;
    }

    @Test
    public void testDisplayPreference_addSummaryTip() {
        mBatteryTipPreferenceController.displayPreference(mPreferenceScreen);

        assertOnlyContainsSummaryTip(mCardPreference);
    }

    @Test
    public void testUpdateBatteryTips_logBatteryTip() {
        mBatteryTipPreferenceController.updateBatteryTips(mOldBatteryTips);

        verify(mFeatureFactory.metricsFeatureProvider).action(mContext,
                MetricsProto.MetricsEvent.ACTION_SUMMARY_TIP,
                BatteryTip.StateType.NEW);
    }

    @Test
    public void testSaveAndRestore() {
        mBatteryTipPreferenceController.updateBatteryTips(mOldBatteryTips);
        final Bundle bundle = new Bundle();
        mBatteryTipPreferenceController.saveInstanceState(bundle);

        final BatteryTipPreferenceController controller = buildBatteryTipPreferenceController();
        controller.mCardPreference = mCardPreference;
        controller.mPrefContext = mContext;
        controller.restoreInstanceState(bundle);

        assertOnlyContainsSummaryTip(mCardPreference);
    }

    @Test
    public void testRestoreFromNull_shouldNotCrash() {
        final Bundle bundle = new Bundle();
        // Battery tip list is null at this time
        mBatteryTipPreferenceController.saveInstanceState(bundle);

        final BatteryTipPreferenceController controller = buildBatteryTipPreferenceController();

        // Should not crash
        controller.restoreInstanceState(bundle);
    }

    @Test
    public void testHandlePreferenceTreeClick_noDialog_invokeCallback() {
        when(mBatteryTip.getType()).thenReturn(SMART_BATTERY_MANAGER);
        List<BatteryTip> batteryTips = new ArrayList<>();
        batteryTips.add(mBatteryTip);
        doReturn(false).when(mBatteryTip).shouldShowDialog();
        doReturn(KEY_TIP).when(mBatteryTip).getKey();
        mBatteryTipPreferenceController.updateBatteryTips(batteryTips);

        mBatteryTipPreferenceController.handlePreferenceTreeClick(mCardPreference);

        verify(mBatteryTipListener).onBatteryTipHandled(mBatteryTip);
    }

    @Test
    public void getAvailabilityStatus_returnAvailableUnsearchable() {
        assertThat(mBatteryTipPreferenceController.getAvailabilityStatus()).isEqualTo(
                BasePreferenceController.AVAILABLE_UNSEARCHABLE);
    }

    private void assertOnlyContainsSummaryTip(CardPreference preference) {
        assertThat(preference.getTitle()).isEqualTo(
                mContext.getString(R.string.battery_tip_summary_title));
        assertThat(preference.getSummary()).isEqualTo(
                mContext.getString(R.string.battery_tip_summary_summary));
    }

    private BatteryTipPreferenceController buildBatteryTipPreferenceController() {
        final BatteryTipPreferenceController controller = new BatteryTipPreferenceController(
                mContext, KEY_PREF);
        controller.setActivity(mSettingsActivity);
        controller.setFragment(mFragment);
        controller.setBatteryTipListener(mBatteryTipListener);

        return controller;
    }
}
