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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

@AnyThread
public final class Collections {
    private Collections() { }

    @FunctionalInterface
    public interface LongKeyRetriever<T> {
        long getKey(@NonNull T value);
    }

    public static @Nullable <T> T find(@NonNull List<T> list, long key,
            @NonNull LongKeyRetriever<T> keyRetriever) {
        int index = binarySearch(list, key, keyRetriever);
        if (index >= 0) {
            return list.get(index);
        }
        return null;
    }

    public static <T> int binarySearch(@NonNull List<T> list, long key,
            @NonNull LongKeyRetriever<T> keyRetriever) {
        int lo = 0;
        int hi = list.size() - 1;

        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            long midKey = keyRetriever.getKey(list.get(mid));

            if (midKey < key) {
                lo = mid + 1;
            } else if (midKey > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return ~lo;
    }
}
