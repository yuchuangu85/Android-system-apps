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

package com.android.car.settings.applications.assist;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowSecureSettings;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.Collections;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowSecureSettings.class})
public class AssistConfigBasePreferenceControllerTest {

    private static class TestAssistConfigBasePreferenceController extends
            AssistConfigBasePreferenceController {

        private int mNumCallsToUpdateState;

        TestAssistConfigBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
            mNumCallsToUpdateState = 0;
        }

        public int getNumCallsToUpdateState() {
            return mNumCallsToUpdateState;
        }

        @Override
        protected void updateState(TwoStatePreference preference) {
            mNumCallsToUpdateState++;
        }

        @Override
        protected List<Uri> getSettingUris() {
            return Collections.singletonList(
                    Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED));
        }
    }

    private static final int TEST_USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";

    private Context mContext;
    private TwoStatePreference mTwoStatePreference;
    private PreferenceControllerTestHelper<TestAssistConfigBasePreferenceController>
            mControllerHelper;
    private TestAssistConfigBasePreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER_ID);

        mContext = RuntimeEnvironment.application;
        mTwoStatePreference = new SwitchPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestAssistConfigBasePreferenceController.class, mTwoStatePreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowSecureSettings.reset();
    }

    @Test
    public void getAvailabilityStatus_hasAssistComponent_isAvailable() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                key, TEST_USER_ID);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noAssistComponent_conditionallyUnavailable() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onStart_registersObserver() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                key, TEST_USER_ID);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT))).isNotEmpty();
        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED))).isNotEmpty();
    }

    @Test
    public void onStop_unregistersObserver() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                key, TEST_USER_ID);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT))).isEmpty();
        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED))).isEmpty();
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                key, TEST_USER_ID);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        int currentCount = mController.getNumCallsToUpdateState();

        ContentObserver observer = Iterables.get(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED)), 0);
        observer.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSIST_STRUCTURE_ENABLED));

        assertThat(mController.getNumCallsToUpdateState()).isEqualTo(currentCount + 1);
    }

    private ShadowContentResolver getShadowContentResolver() {
        return (ShadowContentResolver) Shadows.shadowOf(mContext.getContentResolver());
    }
}
