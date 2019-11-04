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

package com.android.car.pm;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VendorServiceInfoTest {
    private static final String SERVICE_NAME = "com.andorid.car/.MyService";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void emptyString() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse("");
    }

    @Test
    public void multipleHashTags() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse(SERVICE_NAME + "#user=system#bind=bind");
    }

    @Test
    public void unknownArg() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse(SERVICE_NAME + "#user=system,unknownKey=blah");
    }

    @Test
    public void invalidComponentName() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse("invalidComponentName");
    }

    @Test
    public void testServiceNameWithDefaults() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME);
        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
        assertThat(info.isSystemUserService()).isTrue();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.shouldStartOnUnlock()).isTrue();
    }

    @Test
    public void startService() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=start");
        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
    }

    @Test
    public void bindService() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=bind");
        assertThat(info.shouldBeBound()).isTrue();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
    }

    @Test
    public void startServiceInForeground() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=startForeground");
        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isTrue();
    }

    @Test
    public void triggerAsap() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#trigger=asap");
        assertThat(info.shouldStartOnUnlock()).isFalse();
    }

    @Test
    public void triggerUnlocked() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#trigger=userUnlocked");
        assertThat(info.shouldStartOnUnlock()).isTrue();
    }

    @Test
    public void triggerUnknown() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse(SERVICE_NAME + "#trigger=whenever");
    }

    @Test
    public void userScopeForeground() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=foreground");
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isFalse();
    }

    @Test
    public void userScopeSystem() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=system");
        assertThat(info.isForegroundUserService()).isFalse();
        assertThat(info.isSystemUserService()).isTrue();
    }

    @Test
    public void userScopeAll() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=all");
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isTrue();
    }

    @Test
    public void userUnknown() {
        exception.expect(IllegalArgumentException.class);
        VendorServiceInfo.parse(SERVICE_NAME + "#user=whoever");
    }

    @Test
    public void allArgs() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=bind,user=foreground,trigger=userUnlocked");
        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
        assertThat(info.shouldBeBound()).isTrue();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isFalse();
        assertThat(info.shouldStartOnUnlock()).isTrue();
        assertThat(info.shouldStartAsap()).isFalse();
    }
}
