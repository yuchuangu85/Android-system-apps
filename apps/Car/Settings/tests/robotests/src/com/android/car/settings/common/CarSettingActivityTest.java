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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.datetime.DatetimeSettingsFragment;
import com.android.car.settings.testutils.DummyFragment;
import com.android.car.settings.testutils.ShadowCar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/** Unit test for {@link CarSettingActivity}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class CarSettingActivityTest {

    private static final String TEST_TAG = "test_tag";

    private Context mContext;
    private ActivityController<CarSettingActivity> mActivityController;
    private CarSettingActivity mActivity;

    @Mock
    private CarUxRestrictionsManager mMockCarUxRestrictionsManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        CarUxRestrictions noSetupRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* time= */ 0).build();
        when(mMockCarUxRestrictionsManager.getCurrentCarUxRestrictions())
                .thenReturn(noSetupRestrictions);
        ShadowCar.setCarManager(Car.CAR_UX_RESTRICTION_SERVICE, mMockCarUxRestrictionsManager);
        mContext = RuntimeEnvironment.application;
        mActivityController = ActivityController.of(new CarSettingActivity());
        mActivity = mActivityController.get();
        mActivityController.create();
    }

    @Test
    public void launchWithIntent_resolveToFragment() {
        MockitoAnnotations.initMocks(this);
        Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
        CarSettingActivity activity =
                Robolectric.buildActivity(CarSettingActivity.class, intent).setup().get();
        assertThat(activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container))
                .isInstanceOf(DatetimeSettingsFragment.class);
    }

    @Test
    public void launchWithEmptyIntent_resolveToDefaultFragment() {
        CarSettingActivity activity =
                Robolectric.buildActivity(CarSettingActivity.class).setup().get();
        assertThat(activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container))
                .isInstanceOf(DummyFragment.class);
    }

    @Test
    public void onResume_newIntent_launchesNewFragment() {
        mActivityController.start().postCreate(null).resume();
        TestFragment testFragment = new TestFragment();
        mActivity.launchFragment(testFragment);
        assertThat(mActivity.getCurrentFragment()).isEqualTo(testFragment);

        mActivity.onNewIntent(new Intent(Settings.ACTION_DATE_SETTINGS));
        mActivity.onResume();

        assertThat(mActivity.getCurrentFragment()).isNotEqualTo(testFragment);
    }

    @Test
    public void onResume_savedInstanceState_doesNotLaunchFragmentFromOldIntent() {
        mActivityController.start().postCreate(null).resume();
        Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
        mActivity.onNewIntent(intent);
        assertThat(mActivity.getCurrentFragment()).isNotInstanceOf(TestFragment.class);
        mActivity.onResume(); // Showing date time settings (old intent)
        mActivity.launchFragment(new TestFragment()); // Replace with test fragment.

        // Recreate with saved state (e.g. during config change).
        Bundle outState = new Bundle();
        mActivityController.pause().saveInstanceState(outState);
        mActivityController = ActivityController.of(new CarSettingActivity(), intent);
        mActivityController.setup(outState);

        // Should still display most recently launched fragment.
        assertThat(mActivityController.get().getCurrentFragment()).isInstanceOf(TestFragment.class);
    }

    @Test
    public void launchFragment_rootFragment_clearsBackStack() {
        // Add fragment 1
        TestFragment testFragment1 = new TestFragment();
        mActivity.launchFragment(testFragment1);

        // Add fragment 2
        TestFragment testFragment2 = new TestFragment();
        mActivity.launchFragment(testFragment2);

        // Add root fragment
        Fragment root = Fragment.instantiate(mContext,
                mContext.getString(R.string.config_settings_hierarchy_root_fragment));
        mActivity.launchFragment(root);

        assertThat(mActivity.getSupportFragmentManager().getBackStackEntryCount())
                .isEqualTo(1);
    }

    /** Simple Fragment for testing use. */
    public static class TestFragment extends Fragment {
    }
}
