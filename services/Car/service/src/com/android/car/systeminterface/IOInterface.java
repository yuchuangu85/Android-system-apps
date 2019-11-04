/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.systeminterface;

import android.content.Context;

import java.io.File;

/**
 * Interface that abstracts I/O operations
 */
public interface IOInterface {
    /**
     * Returns directory for car service which is /data/system/car.
     * This directory is always available but it should not keep user sensitive data.
     * @return File for the directory.
     */
    File getSystemCarDir();

    class DefaultImpl implements IOInterface {
        private final File mSystemCarDir;

        DefaultImpl(Context context) {
            mSystemCarDir = new File("/data/system/car");
        }

        @Override
        public File getSystemCarDir() {
            return mSystemCarDir;
        }
    }
}
