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

package com.android.car.settings.security;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class})
public class LockTypeBasePreferenceControllerTest {

    // Test classes used to test LockTypeBasePreferenceController.
    private static class TestFragment extends Fragment {
    }

    private static class TestLockPreferenceController extends LockTypeBasePreferenceController {

        TestLockPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Fragment fragmentToOpen() {
            return new TestFragment();
        }

        @Override
        protected int[] allowedPasswordQualities() {
            return new int[]{MATCHING_PASSWORD_QUALITY};
        }
    }

    private static final int MATCHING_PASSWORD_QUALITY =
            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
    private static final int NON_MATCHING_PASSWORD_QUALITY =
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

    private Context mContext;
    private PreferenceControllerTestHelper<TestLockPreferenceController>
            mPreferenceControllerHelper;
    private TestLockPreferenceController mController;
    private Preference mPreference;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestLockPreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void testHandlePreferenceClicked_returnsTrue() {
        assertThat(mController.handlePreferenceClicked(mPreference)).isTrue();
    }

    @Test
    public void testHandlePreferenceClicked_goesToNextFragment() {
        mPreference.performClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).launchFragment(
                any(TestFragment.class));
    }

    @Test
    public void testRefreshUi_isCurrentLock() {
        mController.setCurrentPasswordQuality(MATCHING_PASSWORD_QUALITY);
        mController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.current_screen_lock));
    }

    @Test
    public void testRefreshUi_isNotCurrentLock() {
        mController.setCurrentPasswordQuality(NON_MATCHING_PASSWORD_QUALITY);
        mController.refreshUi();
        assertThat(mPreference.getSummary()).isNotEqualTo(
                mContext.getString(R.string.current_screen_lock));
    }

    @Test
    public void testGetAvailabilityStatus_guestUser() {
        when(mCarUserManagerHelper.isCurrentProcessGuestUser()).thenReturn(true);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void testGetAvailabilityStatus_otherUser() {
        when(mCarUserManagerHelper.isCurrentProcessGuestUser()).thenReturn(false);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }
}
