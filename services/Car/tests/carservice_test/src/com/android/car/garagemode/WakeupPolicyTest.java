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

package com.android.car.garagemode;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class WakeupPolicyTest {
    @Test
    public void testPolicyParser() {
        WakeupPolicy policy;

        policy = new WakeupPolicy(null);
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {""});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"15,1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"15y,1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"15m"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"15m,Q"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"15m,-1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {",1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"m,1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"Qm,1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);

        policy = new WakeupPolicy(new String[] {"-10m,1"});
        assertThat(policy.getWakupIntervalsAmount()).isEqualTo(0);
    }

    @Test
    public void testPolicyInResource() throws Exception {
        // Test that the policy in the resource file parses fine.
        WakeupPolicy policy = new WakeupPolicy(
                getContext().getResources().getStringArray(R.array.config_garageModeCadence));
        assertThat(policy.getWakupIntervalsAmount() > 0).isTrue();
    }

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }
}
