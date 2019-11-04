/*
 * Copyright 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;

import com.android.pump.db.MediaDb;

@AnyThread
public final class Globals {
    private Globals() { }

    public static @NonNull ImageLoader getImageLoader(@NonNull Context context) {
        return getProvider(context).getImageLoader();
    }

    public static @NonNull RecycledViewPool getRecycledViewPool(@NonNull Context context) {
        return getProvider(context).getRecycledViewPool();
    }

    public static @NonNull MediaDb getMediaDb(@NonNull Context context) {
        return getProvider(context).getMediaDb();
    }

    public interface Provider {
        @NonNull ImageLoader getImageLoader();
        @NonNull RecycledViewPool getRecycledViewPool();
        @NonNull MediaDb getMediaDb();
    }

    private static @NonNull Provider getProvider(@NonNull Context context) {
        while (!(context instanceof Provider)) {
            if (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
            } else {
                context = context.getApplicationContext();
                if (!(context instanceof Provider)) {
                    throw new IllegalArgumentException("No global provider in context");
                }
            }
        }
        return (Provider) context;
    }
}
