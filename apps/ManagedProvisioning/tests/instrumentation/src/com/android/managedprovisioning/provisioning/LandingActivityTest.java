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
 * limitations under the License
 */

package com.android.managedprovisioning.provisioning;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.times;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.managedprovisioning.model.ProvisioningParams.EXTRA_PROVISIONING_PARAMS;

import android.content.ComponentName;
import android.content.Intent;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link LandingActivity}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class LandingActivityTest {

    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName("test", "test");

    @Rule
    public IntentsTestRule<LandingActivity> mActivityRule =
            new IntentsTestRule(LandingActivity.class, true, false);


    @Test
    public void onNextButtonClicked_shouldRunPrepareActivity_runsPrepareActivity() {
        ProvisioningParams params = generateProvisioningParamsThatShouldRunPrepareActivity();
        launchProvisioningActivityWithParams(params);

        onView(withText(R.string.next)).perform(click());

        // Has launched the Prepare Activity
        intended(hasComponent(AdminIntegratedFlowPrepareActivity.class.getName()));
    }

    @Test
    public void onNextButtonClicked_shouldNotRunPrepareActivity_doesNotRunPrepareActivity() {
        ProvisioningParams params = generateProvisioningParamsThatShouldNotRunPrepareActivity();
        launchProvisioningActivityWithParams(params);

        onView(withText(R.string.next)).perform(click());

        // Has not launched the Prepare Activity
        intended(hasComponent(AdminIntegratedFlowPrepareActivity.class.getName()), times(0));
    }

    private void launchProvisioningActivityWithParams(ProvisioningParams params) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PROVISIONING_PARAMS, params);
        mActivityRule.launchActivity(intent);
        onView(withId(R.id.setup_wizard_layout));
    }

    private ProvisioningParams generateProvisioningParamsThatShouldRunPrepareActivity() {
        // If we need to connect to wifi then prepare activity should run.
        // For further testing of shouldRunPrepareActivity
        // see {@link AdminIntegratedFlowPrepareActivityTest}
        return createDefaultProvisioningParamsBuilder()
                .setWifiInfo(new WifiInfo.Builder()
                        .setSsid("ssid")
                        .build())
                .build();
    }

    private ProvisioningParams generateProvisioningParamsThatShouldNotRunPrepareActivity() {
        return createDefaultProvisioningParamsBuilder().build();
    }

    private static ProvisioningParams.Builder createDefaultProvisioningParamsBuilder() {
        return new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setProvisioningAction("");
    }
}
