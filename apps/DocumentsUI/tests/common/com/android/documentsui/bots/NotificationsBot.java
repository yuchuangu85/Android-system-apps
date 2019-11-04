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

package com.android.documentsui.bots;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;

import com.android.documentsui.services.TestNotificationService;

import java.io.IOException;

/**
 * A test helper class for controlling notification items.
 */
public class NotificationsBot extends Bots.BaseBot {
    private final ComponentName mComponent;

    public NotificationsBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
        mComponent = new ComponentName(InstrumentationRegistry.getContext(),
                TestNotificationService.class);
    }

    public void setNotificationAccess(Activity activity, boolean enabled) throws IOException {
        if (enabled) {
            mDevice.executeShellCommand(
                    "cmd notification allow_listener " + mComponent.flattenToString());
        } else {
            mDevice.executeShellCommand(
                    "cmd notification disallow_listener " + mComponent.flattenToString());
        }
    }
}
