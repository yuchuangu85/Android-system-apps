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

package com.android.car.dialer.testutils;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;

import androidx.annotation.Nullable;

import org.robolectric.shadows.ShadowApplication;

import java.util.List;

/**
 * Helper class for checking if certain broadcast receiver is registered or unregistered.
 */
public class BroadcastReceiverVerifier {

    private ShadowApplication mShadowApplication;

    public BroadcastReceiverVerifier(Application application) {
        mShadowApplication = shadowOf(application);
    }

    /**
     * Verifies that certain broadcast receiver is registered
     */
    public void verifyReceiverRegistered(String action) {
        assertThat(getReceiverNumber()).isGreaterThan(0);
        assertThat(hasMatchForIntentAction(action)).isTrue();
    }

    /**
     * Verifies that certain broadcast receiver is unregistered
     */
    public void verifyReceiverUnregistered(String action, int preNumber) {
        assertThat(getReceiverNumber()).isLessThan(preNumber);
        assertThat(hasMatchForIntentAction(action)).isFalse();
    }

    /**
     * Returns the BroadcastReceiver with certain Intent filter.
     */
    @Nullable
    public BroadcastReceiver getBroadcastReceiverFor(String action) {
        List<ShadowApplication.Wrapper> wrappers = mShadowApplication.getRegisteredReceivers();
        for (int i = 0; i < wrappers.size(); i++) {
            if (wrappers.get(i).getIntentFilter().hasAction(action)) {
                return wrappers.get(i).getBroadcastReceiver();
            }
        }
        return null;
    }

    /**
     * Returns the number of receivers.
     */
    public int getReceiverNumber() {
        return mShadowApplication.getRegisteredReceivers().size();
    }

    private boolean hasMatchForIntentAction(String action) {
        List<ShadowApplication.Wrapper> wrappers = mShadowApplication.getRegisteredReceivers();
        boolean hasMatch = false;
        for (int i = 0; i < wrappers.size(); i++) {
            if (wrappers.get(i).getIntentFilter().hasAction(action)) {
                hasMatch = true;
            }
        }
        return hasMatch;
    }
}
