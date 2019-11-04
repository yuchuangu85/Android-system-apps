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
import androidx.media2.UriMediaItem;
import androidx.media2.widget.VideoView;

import com.android.pump.R;
import com.android.pump.db.Album;
import com.android.pump.db.Artist;
import com.android.pump.db.Audio;
import com.android.pump.db.Genre;
import com.android.pump.db.Playlist;
import com.android.pump.util.Clog;
import com.android.pump.util.IntentUtils;

@UiThread
public class AudioPlayerActivity extends AppCompatActivity {
    private static final String TAG = Clog.tag(AudioPlayerActivity.class);

    private VideoView mVideoView;

    public static void start(@NonNull Context context, @NonNull Audio audio) {
        // TODO(b/123702587) Find a better URI (audio.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audio.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setDataAndTypeAndNormalize(uri, audio.getMimeType());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    public static void start(@NonNull Context context, @NonNull Album album) {
        // TODO(b/123702587) Find a better URI (album.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                album.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // TODO Should the mime type be MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE?
        intent.setDataAndTypeAndNormalize(uri, MediaStore.Audio.Albums.CONTENT_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    public static void start(@NonNull Context context, @NonNull Artist artist) {
        // TODO(b/123702587) Find a better URI (artist.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                artist.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // TODO Should the mime type be MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE?
        intent.setDataAndTypeAndNormalize(uri, MediaStore.Audio.Artists.CONTENT_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    public static void start(@NonNull Context context, @NonNull Genre genre) {
        // TODO(b/123702587) Find a better URI (genre.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                genre.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // TODO Should the mime type be MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE?
        intent.setDataAndTypeAndNormalize(uri, MediaStore.Audio.Genres.CONTENT_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    public static void start(@NonNull Context context, @NonNull Playlist playlist) {
        // TODO(b/123702587) Find a better URI (playlist.getUri()?)
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                playlist.getId());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // TODO Should the mime type be MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE?
        intent.setDataAndTypeAndNormalize(uri, MediaStore.Audio.Playlists.CONTENT_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    public static void start(@NonNull Context context, @NonNull Playlist playlist, int position) {
        // TODO(b/123702587) Find a better URI?
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                playlist.getId()).buildUpon()
                    .appendEncodedPath(MediaStore.Audio.Playlists.Members.CONTENT_DIRECTORY)
                    .appendEncodedPath(String.valueOf(position)).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // TODO Should the mime type be MediaStore.Audio.Playlists.CONTENT_TYPE?
        intent.setDataAndTypeAndNormalize(uri, MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        IntentUtils.startExternalActivity(context, intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        mVideoView = findViewById(R.id.video_view);

        handleIntent();
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
}
