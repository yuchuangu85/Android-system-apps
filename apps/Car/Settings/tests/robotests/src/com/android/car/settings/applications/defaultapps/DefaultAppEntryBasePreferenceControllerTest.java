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

package com.android.car.settings.applications.defaultapps;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.applications.DefaultAppInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class DefaultAppEntryBasePreferenceControllerTest {
    private static final CharSequence TEST_LABEL = "Test Label";

    private static class TestDefaultAppEntryBasePreferenceController extends
            DefaultAppEntryBasePreferenceController<Preference> {

        private DefaultAppInfo mDefaultAppInfo;

        TestDefaultAppEntryBasePreferenceController(Context context,
                String preferenceKey, FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }

        @Nullable
        @Override
        protected DefaultAppInfo getCurrentDefaultAppInfo() {
            return mDefaultAppInfo;
        }

        protected void setCurrentDefaultAppInfo(DefaultAppInfo defaultAppInfo) {
            mDefaultAppInfo = defaultAppInfo;
        }
    }

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<TestDefaultAppEntryBasePreferenceController>
            mControllerHelper;
    private TestDefaultAppEntryBasePreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestDefaultAppEntryBasePreferenceController.class,
                mPreference);
        mController = mControllerHelper.getController();
    }

    @Test
    public void refreshUi_hasDefaultAppWithLabel_summaryAndIconAreSet() {
        DefaultAppInfo defaultAppInfo = mock(DefaultAppInfo.class);
        when(defaultAppInfo.loadLabel()).thenReturn(TEST_LABEL);
        when(defaultAppInfo.loadIcon()).thenReturn(mContext.getDrawable(R.drawable.test_icon));
        mController.setCurrentDefaultAppInfo(defaultAppInfo);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(TEST_LABEL);
        assertThat(mPreference.getIcon()).isNotNull();
    }

    @Test
    public void refreshUi_hasDefaultAppWithoutLabel_summaryAndIconAreNotSet() {
        DefaultAppInfo defaultAppInfo = mock(DefaultAppInfo.class);
        when(defaultAppInfo.loadLabel()).thenReturn(null);
        when(defaultAppInfo.loadIcon()).thenReturn(null);
        mController.setCurrentDefaultAppInfo(defaultAppInfo);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.app_list_preference_none));
        assertThat(mPreference.getIcon()).isNull();
    }

    @Test
    public void refreshUi_hasNoDefaultApp_summaryAndIconAreNotSet() {
        mController.setCurrentDefaultAppInfo(null);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.app_list_preference_none));
        assertThat(mPreference.getIcon()).isNull();
    }
}
