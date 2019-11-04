/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.common.compat;

import android.content.Context;
import android.media.tv.TvInputService.RecordingSession;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import com.android.tv.common.compat.api.RecordingSessionCompatCommands;
import com.android.tv.common.compat.api.RecordingSessionCompatEvents;
import com.android.tv.common.compat.api.SessionEventNotifier;
import com.android.tv.common.compat.internal.RecordingSessionCompatProcessor;

/**
 * TIF Compatibility for {@link RecordingSession}.
 *
 * <p>Extends {@code RecordingSession} in a backwards compatible way.
 */
@RequiresApi(api = VERSION_CODES.N)
public abstract class RecordingSessionCompat extends RecordingSession
        implements SessionEventNotifier,
                RecordingSessionCompatCommands,
                RecordingSessionCompatEvents {

    private final RecordingSessionCompatProcessor mProcessor;

    public RecordingSessionCompat(Context context) {
        super(context);
        mProcessor = new RecordingSessionCompatProcessor(this, this);
    }

    @Override
    public void onAppPrivateCommand(String action, Bundle data) {
        if (!mProcessor.handleAppPrivateCommand(action, data)) {
            super.onAppPrivateCommand(action, data);
        }
    }

    /** Display a debug message to the session for display on dev builds only */
    @Override
    public void onDevMessage(String message) {}

    /** Notify the client to Display a message in the application as a toast on dev builds only. */
    @Override
    public void notifyDevToast(String message) {
        mProcessor.notifyDevToast(message);
    }

    /** Notify the client Recording started. */
    @Override
    public void notifyRecordingStarted(String uri) {
        mProcessor.notifyRecordingStarted(uri);
    }
}
