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
package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.OperationMonitor;

/**
 * ContentLock provides a mechanism to block content from reloading while selection
 * activities like gesture and band selection are active. Clients using live data
 * (data loaded, for example by a {@link Loader}), should route calls to load
 * content through this lock using {@link ContentLock#runWhenUnlocked(Runnable)}.
 */
public final class ContentLock {

    private static final String TAG = "ContentLock";

    private final OperationMonitor mMonitor = new OperationMonitor();

    @GuardedBy("this")
    private @Nullable Runnable mCallback;

    public ContentLock() {
        mMonitor.addListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "monitor listener, is locked : " + isLocked());
            }
            if (!isLocked()) {
                synchronized (this) {
                    final Runnable callback = mCallback;
                    if (callback != null) {
                        callback.run();
                        mCallback = null;
                    }
                }
            }
        });
    }

    public OperationMonitor getMonitor() {
        return mMonitor;
    }

    /**
     * Returns true if locked.
     */
    private boolean isLocked() {
        return mMonitor.isStarted();
    }

    /**
     * Attempts to run the given Runnable if not-locked, or else the Runnable is set to be ran next
     * (replacing any previous set Runnables).
     */
    public synchronized void runWhenUnlocked(Runnable runnable) {
        if (DEBUG) {
            Log.d(TAG, "run when unlock, is locked : " + isLocked());
        }
        if (!isLocked()) {
            runnable.run();
        } else {
            mCallback = runnable;
        }
    }
}
