/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import static com.android.car.settings.system.MasterClearFragment.CHECK_LOCK_REQUEST_CODE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.Activity;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.security.CheckLockActivity;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowUserManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.Collections;

/** Unit test for {@link MasterClearFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowUserManager.class})
public class MasterClearFragmentTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    private MasterClearFragment mFragment;

    @Before
    public void setUp() {
        // Setup needed by instantiated PreferenceControllers.
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(Collections.emptyList());

        mFragment = FragmentController.of(new MasterClearFragment()).setup();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowUserManager.reset();
    }

    @Test
    public void masterClearButtonClicked_launchesCheckLockActivity() {
        findMasterClearButton(mFragment.requireActivity()).performClick();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(startedIntent.getComponent().getClassName()).isEqualTo(
                CheckLockActivity.class.getName());
    }

    @Test
    public void processActivityResult_resultOk_launchesMasterClearConfirmFragment() {
        mFragment.processActivityResult(CHECK_LOCK_REQUEST_CODE, RESULT_OK, /* data= */ null);

        Fragment launchedFragment = mFragment.getFragmentManager().findFragmentById(
                R.id.fragment_container);

        assertThat(launchedFragment).isInstanceOf(MasterClearConfirmFragment.class);
    }

    @Test
    public void processActivityResult_otherResultCode_doesNothing() {
        mFragment.processActivityResult(CHECK_LOCK_REQUEST_CODE, RESULT_CANCELED, /* data= */ null);

        Fragment launchedFragment = mFragment.getFragmentManager().findFragmentById(
                R.id.fragment_container);

        assertThat(launchedFragment).isInstanceOf(MasterClearFragment.class);
    }

    private Button findMasterClearButton(Activity activity) {
        return activity.findViewById(R.id.action_button1);
    }
}
