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
package com.android.wallpaper.util;

/**
 * Analyzes Throwables.
 */
public class ThrowableAnalyzer {

    /**
     * Returns true if the given Throwable or if any Throwables in its cause chain is an
     * OutOfMemoryError.
     */
    public static boolean isOOM(Throwable throwable) {
        // An OOM could have been in any "cause" of the Throwable so dig through all the causes to
        // make sure.
        while (throwable != null) {
            if (throwable instanceof OutOfMemoryError) {
                return true;
            }
            throwable = throwable.getCause();
        }

        return false;
    }
}
