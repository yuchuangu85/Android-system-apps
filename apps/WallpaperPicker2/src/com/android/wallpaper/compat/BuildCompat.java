/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.compat;

import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/**
 * Provides the SDK version in a manner that can be stubbed out in a test environment.
 */
public class BuildCompat {
    public static final int JB_MR2_SDK_VERSION = VERSION_CODES.JELLY_BEAN_MR2;
    public static final int L_SDK_VERSION = VERSION_CODES.LOLLIPOP;
    public static final int N_SDK_VERSION = Build.VERSION_CODES.N;
    public static final int N_MR1_SDK_VERSION = Build.VERSION_CODES.N_MR1;

    private static int sSdk = Build.VERSION.SDK_INT;

    /**
     * Returns whether the framework on the current Android device is JellyBean MR2 (API 18) or
     * higher. Used to determine if it's safe to use APIs added in that API version such as
     * HandlerThread#quitSafely.
     */
    public static boolean isAtLeastJBMR2() {
        return sSdk >= JB_MR2_SDK_VERSION;
    }

    /**
     * Returns whether the framework on the current Android device is L (API 21) or higher. Used to
     * determine whether framework classes introduced in L such as JobScheduler can be used on this
     * device.
     */
    public static boolean isAtLeastL() {
        return sSdk >= L_SDK_VERSION;
    }

    /**
     * Returns whether the framework on the current Android device is N or higher. Used to determine
     * whether new N-specific wallpaper APIs are available.
     */
    public static boolean isAtLeastN() {
        return sSdk >= N_SDK_VERSION;
    }

    /**
     * Returns whether the framework on the current Android device is N-MR1 or higher. Used to
     * determine whether new N-MR1-specific wallpaper APIs are available.
     */
    public static boolean isAtLeastNMR1() {
        return sSdk >= N_MR1_SDK_VERSION;
    }

    /**
     * Returns whether the framework on the current Android device is N-MR2 or higher. Used to
     * determine if new N-MR2 specific API behavior is present on the device.
     */
    public static boolean isAtLeastNMR2() {
        return sSdk > N_MR1_SDK_VERSION
                || (sSdk == N_MR1_SDK_VERSION && VERSION.RELEASE.equals("7.1.2"));
    }

    /**
     * Returns whether the framework on the current Android device is O or higher.
     */
    public static boolean isAtLeastO() {
        return sSdk >= Build.VERSION_CODES.O;
    }

    /**
     * Returns whether the framework on the current Android device is O-MR1 or higher.
     */
    public static boolean isAtLeastOMR1() {
        return sSdk >= VERSION_CODES.O_MR1;
    }

    /**
     * Sets the SDK version that BuildCompat will consider the current device to be on. Used for
     * testing only.
     */
    public static void setSdkVersionForTesting(int sdk) {
        sSdk = sdk;
    }

    public static boolean isAtLeastQ() {
        return sSdk >= VERSION_CODES.Q;
    }
}
