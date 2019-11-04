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

import android.os.RemoteException;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.settingslib.applications.ApplicationsState;

import java.util.List;

/**
 * Bridges the value of {@link ISms#getPremiumSmsPermission(String)} into the {@link
 * ApplicationsState.AppEntry#extraInfo} for each entry's package name.
 */
public class AppStatePremiumSmsBridge implements AppEntryListManager.ExtraInfoBridge {

    private final ISms mSmsManager;

    public AppStatePremiumSmsBridge(ISms smsManager) {
        mSmsManager = smsManager;
    }

    @Override
    public void loadExtraInfo(List<ApplicationsState.AppEntry> entries) {
        for (ApplicationsState.AppEntry entry : entries) {
            entry.extraInfo = getSmsState(entry.info.packageName);
        }
    }

    private int getSmsState(String packageName) {
        try {
            return mSmsManager.getPremiumSmsPermission(packageName);
        } catch (RemoteException e) {
            return SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN;
        }
    }
}
