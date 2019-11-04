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
package com.android.server.telecom;

import android.os.Handler;
import android.telecom.Logging.Runnable;

import java.util.concurrent.Executor;

/** An executor that starts a log session before executing a runnable */
public class LoggedHandlerExecutor implements Executor {
    private Handler mHandler;
    private String mSessionName;
    private TelecomSystem.SyncRoot mLock;

    public LoggedHandlerExecutor(Handler handler, String sessionName,
            TelecomSystem.SyncRoot lock) {
        mHandler = handler;
        mSessionName = sessionName;
        mLock = lock;
    }

    @Override
    public void execute(java.lang.Runnable command) {
        mHandler.post(new Runnable(mSessionName, mLock) {
            @Override
            public void loggedRun() {
                command.run();
            }
        }.prepare());
    }
}
