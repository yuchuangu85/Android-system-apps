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

package com.android.car.settings.testutils;

import android.bluetooth.BluetoothAdapter;

import com.android.settingslib.bluetooth.LocalBluetoothAdapter;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(LocalBluetoothAdapter.class)
public class ShadowLocalBluetoothAdapter {

    private int mState = BluetoothAdapter.STATE_OFF;
    private boolean mIsBluetoothEnabled = true;
    private int mScanMode = BluetoothAdapter.SCAN_MODE_NONE;

    @Implementation
    protected boolean isEnabled() {
        return mIsBluetoothEnabled;
    }

    @Implementation
    protected boolean enable() {
        mIsBluetoothEnabled = true;
        return true;
    }

    @Implementation
    protected boolean disable() {
        mIsBluetoothEnabled = false;
        return true;
    }

    @Implementation
    protected int getScanMode() {
        return mScanMode;
    }

    @Implementation
    protected void setScanMode(int mode) {
        mScanMode = mode;
    }

    @Implementation
    protected boolean setScanMode(int mode, int duration) {
        mScanMode = mode;
        return true;
    }

    @Implementation
    protected int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }
}
