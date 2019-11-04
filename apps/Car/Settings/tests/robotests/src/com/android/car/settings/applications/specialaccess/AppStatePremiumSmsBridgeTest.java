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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.os.RemoteException;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

/** Unit test for {@link AppStatePremiumSmsBridge}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class AppStatePremiumSmsBridgeTest {

    @Mock
    private ISms mSmsManager;
    private AppStatePremiumSmsBridge mBridge;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBridge = new AppStatePremiumSmsBridge(mSmsManager);
    }

    @Test
    public void loadExtraInfo() throws RemoteException {
        String package1 = "test.package1";
        AppEntry appEntry1 = createAppEntry(package1);
        int value1 = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW;
        when(mSmsManager.getPremiumSmsPermission(package1)).thenReturn(value1);

        String package2 = "test.package2";
        AppEntry appEntry2 = createAppEntry(package2);
        int value2 = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW;
        when(mSmsManager.getPremiumSmsPermission(package2)).thenReturn(value2);

        mBridge.loadExtraInfo(Arrays.asList(appEntry1, appEntry2));

        assertThat(appEntry1.extraInfo).isEqualTo(value1);
        assertThat(appEntry2.extraInfo).isEqualTo(value2);
    }

    private AppEntry createAppEntry(String packageName) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;

        AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        appEntry.label = packageName;

        return appEntry;
    }
}
