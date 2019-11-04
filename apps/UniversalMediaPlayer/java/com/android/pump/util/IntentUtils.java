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

package com.android.pump.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;

import com.android.pump.activity.ActivityStarterActivity;

@UiThread
public final class IntentUtils {
    private IntentUtils() { }

    public static void startExternalActivity(@NonNull Context context, @NonNull Intent intent) {
        startExternalActivity(context, intent, null);
    }

    public static void startExternalActivity(@NonNull Context context, @NonNull Intent intent,
            @Nullable Bundle options) {
        Intent startIntent = ActivityStarterActivity.createStartIntent(context, intent, options);
        context.startActivity(startIntent);
    }

    public static void startExternalActivityForResult(@NonNull Activity activity,
            @NonNull Intent intent, int requestCode) {
        startExternalActivityForResult(activity, intent, requestCode, null);
    }

    public static void startExternalActivityForResult(@NonNull Activity activity,
            @NonNull Intent intent, int requestCode, @Nullable Bundle options) {
        Intent startIntent = ActivityStarterActivity.createStartIntent(activity, intent, options);
        activity.startActivityForResult(startIntent, requestCode);
    }

    public static void startExternalActivityForResult(@NonNull Fragment fragment,
            @NonNull Intent intent, int requestCode) {
        startExternalActivityForResult(fragment, intent, requestCode, null);
    }

    public static void startExternalActivityForResult(@NonNull Fragment fragment,
            @NonNull Intent intent, int requestCode, @Nullable Bundle options) {
        Context context = fragment.requireContext();
        Intent startIntent = ActivityStarterActivity.createStartIntent(context, intent, options);
        fragment.startActivityForResult(startIntent, requestCode);
    }
}
