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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ValidatedEditTextPreference;
import com.android.car.settings.testutils.ShadowCarWifiManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class})
public class WifiTetherBasePreferenceControllerTest {

    private static final String SUMMARY = "SUMMARY";
    private static final String DEFAULT_SUMMARY = "DEFAULT_SUMMARY";

    private static class TestWifiTetherBasePreferenceController extends
            WifiTetherBasePreferenceController<Preference> {

        private String mSummary;
        private String mDefaultSummary;

        TestWifiTetherBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }

        @Override
        protected String getSummary() {
            return mSummary;
        }

        @Override
        protected String getDefaultSummary() {
            return mDefaultSummary;
        }

        protected void setConfigSummaries(@Nullable String summary,
                @Nullable String defaultSummary) {
            mSummary = summary;
            mDefaultSummary = defaultSummary;
        }
    }

    private Context mContext;
    private ValidatedEditTextPreference mPreference;
    private PreferenceControllerTestHelper<TestWifiTetherBasePreferenceController>
            mControllerHelper;
    private TestWifiTetherBasePreferenceController mController;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mPreference = new ValidatedEditTextPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<TestWifiTetherBasePreferenceController>(mContext,
                        TestWifiTetherBasePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
    }

    @Test
    public void onStart_shouldStartCarWifiManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(getShadowCarWifiManager().getCurrentState())
                .isEqualTo(getShadowCarWifiManager().STATE_STARTED);
    }

    @Test
    public void onStop_shouldStopCarWifiManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(getShadowCarWifiManager().getCurrentState())
                .isEqualTo(getShadowCarWifiManager().STATE_STOPPED);
    }

    @Test
    public void onDestroy_shouldDestroyCarWifiManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        assertThat(getShadowCarWifiManager().getCurrentState())
                .isEqualTo(getShadowCarWifiManager().STATE_DESTROYED);
    }

    @Test
    public void noSummaryToShow_defaultSummarySet_shouldShowDefaultSummary() {
        mController.setConfigSummaries(/* summary= */ null, DEFAULT_SUMMARY);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.updateState(mPreference);

        assertThat(mPreference.getSummary()).isEqualTo(DEFAULT_SUMMARY);
    }

    @Test
    public void noSummaryToShow_defaultSummaryNotSet_shouldNotShowSummary() {
        mController.setConfigSummaries(/* summary= */ null, /* defaultSummary= */ null);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.updateState(mPreference);

        assertThat(mPreference.getSummary()).isEqualTo(null);
    }

    @Test
    public void summaryToShow_defaultSummarySet_shouldShowNonDefaultSummary() {
        mController.setConfigSummaries(SUMMARY, DEFAULT_SUMMARY);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.updateState(mPreference);

        assertThat(mPreference.getSummary()).isEqualTo(SUMMARY);
    }

    @Test
    public void summaryToShow_defaultSummaryNotSet_shouldSHowNonDefaultSummary() {
        mController.setConfigSummaries(SUMMARY, /* defaultSummary= */ null);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.updateState(mPreference);

        assertThat(mPreference.getSummary()).isEqualTo(SUMMARY);
    }

    private ShadowCarWifiManager getShadowCarWifiManager() {
        return Shadow.extract(new CarWifiManager(mContext));
    }
}
