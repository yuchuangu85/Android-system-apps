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

package com.android.pump.concurrent;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@AnyThread
public final class Executors {
    private Executors() { }

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final Executor MAIN_THREAD_EXECUTOR = new Executor() {
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            if (mHandler.getLooper() != Looper.myLooper()) {
                mHandler.post(command);
            } else {
                command.run();
            }
        }
    };
    private static final Executor UI_THREAD_EXECUTOR = MAIN_THREAD_EXECUTOR;

    public static @NonNull Executor directExecutor() {
        return DIRECT_EXECUTOR;
    }

    public static @NonNull Executor mainThreadExecutor() {
        return MAIN_THREAD_EXECUTOR;
    }

    public static @NonNull Executor uiThreadExecutor() {
        return UI_THREAD_EXECUTOR;
    }

    public static @NonNull ExecutorService newFixedUniqueThreadPool(int nThreads) {
        return new UniqueExecutor(nThreads, nThreads, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    public static @NonNull ExecutorService newFixedUniqueThreadPool(int nThreads,
            @NonNull ThreadFactory threadFactory) {
        return new UniqueExecutor(nThreads, nThreads, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
    }

    public static @NonNull ExecutorService newCachedUniqueThreadPool() {
        return new UniqueExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
    }

    public static @NonNull ExecutorService newCachedUniqueThreadPool(
            @NonNull ThreadFactory threadFactory) {
        return new UniqueExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
    }
}
