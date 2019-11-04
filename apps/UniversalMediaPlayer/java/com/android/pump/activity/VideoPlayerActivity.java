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

package com.android.pump.activity;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media2.MediaController;
import androidx.media2.MediaItem;
import androidx.media2.SessionPlayer;
import androidx.media2.SessionToken;
import androidx.media2.UriMediaItem;
import androidx.media2.widget.VideoView;

import com.android.pump.R;
import com.android.pump.concurrent.Executors;
import com.android.pump.db.Video;
import com.android.pump.util.Clog;
import com.android.pump.util.IntentUtils;

@UiThread
public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = Clog.tag(VideoPlayerActivity.class);
    private static final String SAVED_POSITION_KEY = "SavedPosition";

    private VideoView mVideoView;
    private MediaController mMediaController;
    private long mSavedPosition = SessionPlayer.UNKNOWN_TIME;

    public static void start(@NonNull Context context, @NonNull Video video) {
        // TODO(b/123703220) Find a better URI (video.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                video.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setDataAndTypeAndNormalize(uri, video.getMimeType());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        mVideoView = findViewById(R.id.video_view);

        if (savedInstanceState != null) {
            mSavedPosition = savedInstanceState.getLong(SAVED_POSITION_KEY,
                    SessionPlayer.UNKNOWN_TIME);
        }

        handleIntent();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mMediaController != null) {
            outState.putLong(SAVED_POSITION_KEY, mMediaController.getCurrentPosition());
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttachedToWindow() {
        if (mMediaController == null) {
            SessionToken token = mVideoView.getSessionToken();
            mMediaController = new MediaController(this, token, Executors.uiThreadExecutor(),
                    new ControllerCallback());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mMediaController != null) {
            mMediaController.close();
            mMediaController = null;
        }
    }

    @Override
    protected void onNewIntent(@Nullable Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        handleIntent();
    }

    private void handleIntent() {
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri == null) {
            Clog.e(TAG, "The intent has no uri. Finishing activity...");
            finish();
            return;
        }
        UriMediaItem mediaItem = new UriMediaItem.Builder(uri).build();
        mVideoView.setMediaItem(mediaItem);
    }

    private class ControllerCallback extends MediaController.ControllerCallback {
        @Override
        public void onCurrentMediaItemChanged(@NonNull MediaController controller,
                @Nullable MediaItem item) {
            if (mSavedPosition != SessionPlayer.UNKNOWN_TIME) {
                controller.seekTo(mSavedPosition);
                mSavedPosition = SessionPlayer.UNKNOWN_TIME;
            }
            controller.play();
        }
    }
}
