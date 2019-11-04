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
 * limitations under the License.
 */
package com.android.tv.audiotvservice;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import com.android.tv.data.ChannelImpl;
import com.android.tv.data.StreamInfo;
import com.android.tv.data.api.Channel;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnTuneListener;

/** Foreground service for audio-only TV inputs. */
public class AudioOnlyTvService extends Service implements OnTuneListener {
    // TODO(b/110969180): implement this service.
    private static final String TAG = "AudioOnlyTvService";
    private static final int NOTIFICATION_ID = 1;

    @Nullable private String mTvInputId;
    private TunableTvView mTvView;
    // TODO(b/110969180): perhaps use MediaSessionWrapper
    private MediaSession mMediaSession;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return null;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        // TODO(b/110969180): create TvView

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand. flags = " + flags + ", startId = " + startId);
        // TODO(b/110969180): real notification and or media session
        startForeground(NOTIFICATION_ID, new Notification());
        mTvInputId = AudioOnlyTvServiceUtil.getInputIdFromIntent(intent);
        tune(mTvInputId);
        return START_STICKY;
    }

    private void tune(String tvInputId) {
        Channel channel = ChannelImpl.createPassthroughChannel(tvInputId);
        mTvView.tuneTo(channel, null, this);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        mTvInputId = null;
        // TODO(b/110969180): clear TvView
    }

    // TODO(b/110969180): figure out when to stop ourselves,  mediaSession event?

    // TODO(b/110969180): handle OnTuner Listener
    @Override
    public void onTuneFailed(Channel channel) {}

    @Override
    public void onUnexpectedStop(Channel channel) {}

    @Override
    public void onStreamInfoChanged(StreamInfo info, boolean allowAutoSelectionOfTrack) {}

    @Override
    public void onChannelRetuned(Uri channel) {}

    @Override
    public void onContentBlocked() {}

    @Override
    public void onContentAllowed() {}

    @Override
    public void onChannelSignalStrength() {}
}
