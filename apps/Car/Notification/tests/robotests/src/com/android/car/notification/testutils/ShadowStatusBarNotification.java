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

package com.android.car.notification.testutils;

import android.content.Context;
import android.service.notification.StatusBarNotification;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(value = StatusBarNotification.class)
public class ShadowStatusBarNotification {

    private static Context sContext;

    @Implementation
    public Context getPackageContext(Context context) {
        return sContext;
    }

    public static void setContext(Context context) {
        sContext = context;
    }
}
