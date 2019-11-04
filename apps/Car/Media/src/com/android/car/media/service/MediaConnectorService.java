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

package com.android.car.media.service;

import android.content.Intent;
import android.os.IBinder;

import androidx.lifecycle.LifecycleService;
import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * This service is started by CarMediaService when a new user is unlocked. It connects to the
 * media source provided by CarMediaService and calls prepare() on the active MediaSession.
 *
 * TODO(b/139497602): merge this class into CarMediaService, so it doesn't depend on Media Center
 */
public class MediaConnectorService extends LifecycleService {
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        PlaybackViewModel playbackViewModel = PlaybackViewModel.get(getApplication());
        playbackViewModel.getPlaybackController().observe(this,
                playbackController -> {
                    if (playbackController != null) playbackController.prepare();
                });

        return START_NOT_STICKY;
    }
}
