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

package com.android.pump.db;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.pump.provider.Query;
import com.android.pump.util.Clog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

@WorkerThread
class VideoStore extends ContentObserver {
    private static final String TAG = Clog.tag(VideoStore.class);

    // TODO Replace the following with MediaStore.Video.Media.RELATIVE_PATH throughout the code.
    private static final String RELATIVE_PATH = "relative_path";

    // TODO Replace with Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q throughout the code.
    private static boolean isRunningQ() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.P
                && Build.VERSION.PREVIEW_SDK_INT > 0);
    }

    private final ContentResolver mContentResolver;
    private final ChangeListener mChangeListener;
    private final MediaProvider mMediaProvider;

    interface ChangeListener {
        void onMoviesAdded(@NonNull Collection<Movie> movies);
        void onSeriesAdded(@NonNull Collection<Series> series);
        void onEpisodesAdded(@NonNull Collection<Episode> episodes);
        void onOthersAdded(@NonNull Collection<Other> others);
    }

    @AnyThread
    VideoStore(@NonNull ContentResolver contentResolver, @NonNull ChangeListener changeListener,
            @NonNull MediaProvider mediaProvider) {
        super(null);

        Clog.i(TAG, "VideoStore(" + contentResolver + ", " + changeListener
                + ", " + mediaProvider + ")");
        mContentResolver = contentResolver;
        mChangeListener = changeListener;
        mMediaProvider = mediaProvider;

        // TODO(b/123706961) Do we need content observer for other content uris? (E.g. thumbnail)
        mContentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true, this);

        // TODO(b/123706961) When to call unregisterContentObserver?
        // mContentResolver.unregisterContentObserver(this);
    }

    void load() {
        Clog.i(TAG, "load()");
        Collection<Movie> movies = new ArrayList<>();
        Collection<Series> series = new ArrayList<>();
        Collection<Episode> episodes = new ArrayList<>();
        Collection<Other> others = new ArrayList<>();

        /* TODO get via count instead?
                Cursor countCursor = mContentResolver.query(CONTENT_URI,
                new String[] { "count(*) AS count" },
                null,
                null,
                null);
        countCursor.moveToFirst();
        int count = countCursor.getInt(0);
        Clog.i(TAG, "count = " + count);
        countCursor.close();
        */

        {
            Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection;
            if (isRunningQ()) {
                projection = new String[] {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.MIME_TYPE,
                    RELATIVE_PATH,
                    MediaStore.Video.Media.DISPLAY_NAME
                };
            } else {
                projection = new String[] {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATA
                };
            }
            String sortOrder = MediaStore.Video.Media._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, null, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int dataColumn;
                    int relativePathColumn;
                    int displayNameColumn;
                    int mimeTypeColumn = cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.MIME_TYPE);

                    if (isRunningQ()) {
                        dataColumn = -1;
                        relativePathColumn = cursor.getColumnIndexOrThrow(RELATIVE_PATH);
                        displayNameColumn = cursor.getColumnIndexOrThrow(
                                MediaStore.Video.Media.DISPLAY_NAME);
                    } else {
                        dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                        relativePathColumn = -1;
                        displayNameColumn = -1;
                    }

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String mimeType = cursor.getString(mimeTypeColumn);

                        File file;
                        if (isRunningQ()) {
                            String relativePath = cursor.getString(relativePathColumn);
                            String displayName = cursor.getString(displayNameColumn);
                            file = new File(relativePath, displayName);
                        } else {
                            String data = cursor.getString(dataColumn);
                            file = new File(data);
                        }
                        Query query = Query.parse(Uri.fromFile(file));
                        if (query.isMovie()) {
                            Movie movie;
                            if (query.hasYear()) {
                                movie = new Movie(id, mimeType, query.getName(), query.getYear());
                            } else {
                                movie = new Movie(id, mimeType, query.getName());
                            }
                            movies.add(movie);
                        } else if (query.isEpisode()) {
                            Series serie = null;
                            for (Series s : series) {
                                if (s.getTitle().equals(query.getName())
                                        && s.hasYear() == query.hasYear()
                                        && (!s.hasYear() || s.getYear() == query.getYear())) {
                                    serie = s;
                                    break;
                                }
                            }
                            if (serie == null) {
                                if (query.hasYear()) {
                                    serie = new Series(query.getName(), query.getYear());
                                } else {
                                    serie = new Series(query.getName());
                                }
                                series.add(serie);
                            }

                            Episode episode = new Episode(id, mimeType, serie,
                                    query.getSeason(), query.getEpisode());
                            episodes.add(episode);

                            serie.addEpisode(episode);
                        } else {
                            Other other = new Other(id, mimeType, query.getName());
                            others.add(other);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        mChangeListener.onMoviesAdded(movies);
        mChangeListener.onSeriesAdded(series);
        mChangeListener.onEpisodesAdded(episodes);
        mChangeListener.onOthersAdded(others);
    }

    boolean loadData(@NonNull Movie movie) {
        Uri thumbnailUri = getThumbnailUri(movie.getId());
        if (thumbnailUri != null) {
            return movie.setThumbnailUri(thumbnailUri);
        }
        return false;
    }

    boolean loadData(@NonNull Series series) {
        return false;
    }

    boolean loadData(@NonNull Episode episode) {
        Uri thumbnailUri = getThumbnailUri(episode.getId());
        if (thumbnailUri != null) {
            return episode.setThumbnailUri(thumbnailUri);
        }
        return false;
    }

    boolean loadData(@NonNull Other other) {
        boolean updated = false;

        Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.LATITUDE,
            MediaStore.Video.Media.LONGITUDE
        };
        String selection = MediaStore.Video.Media._ID + " = ?";
        String[] selectionArgs = { Long.toString(other.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN);
                int latitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.LATITUDE);
                int longitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.LONGITUDE);

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(titleColumn)) {
                        String title = cursor.getString(titleColumn);
                        updated |= other.setTitle(title);
                    }
                    if (!cursor.isNull(durationColumn)) {
                        long duration = cursor.getLong(durationColumn);
                        updated |= other.setDuration(duration);
                    }
                    if (!cursor.isNull(dateTakenColumn)) {
                        long dateTaken = cursor.getLong(dateTakenColumn);
                        updated |= other.setDateTaken(dateTaken);
                    }
                    if (!cursor.isNull(latitudeColumn) && !cursor.isNull(longitudeColumn)) {
                        double latitude = cursor.getDouble(latitudeColumn);
                        double longitude = cursor.getDouble(longitudeColumn);
                        updated |= other.setLatLong(latitude, longitude);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        Uri thumbnailUri = getThumbnailUri(other.getId());
        if (thumbnailUri != null) {
            updated |= other.setThumbnailUri(thumbnailUri);
        }

        return updated;
    }

    private @Nullable Uri getThumbnailUri(long id) {
        int thumbKind = MediaStore.Video.Thumbnails.MINI_KIND;

        // TODO(b/123707512) The following line is required to generate thumbnails -- is there a better way?
        MediaStore.Video.Thumbnails.getThumbnail(mContentResolver, id, thumbKind, null);

        Uri thumbnailUri = null;
        Uri contentUri = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Video.Thumbnails.DATA
        };
        String selection = MediaStore.Video.Thumbnails.KIND + " = " + thumbKind + " AND " +
                MediaStore.Video.Thumbnails.VIDEO_ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA);

                if (cursor.moveToFirst()) {
                    String data = cursor.getString(dataColumn);

                    thumbnailUri = Uri.fromFile(new File(data));
                }
            } finally {
                cursor.close();
            }
        }
        return thumbnailUri;
    }

    @Override
    public void onChange(boolean selfChange) {
        Clog.i(TAG, "onChange(" + selfChange + ")");
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        Clog.i(TAG, "onChange(" + selfChange + ", " + uri + ")");
        // TODO(b/123706961) Figure out what changed
        // onChange(false, content://media)
        // onChange(false, content://media/external)
        // onChange(false, content://media/external/audio/media/444)
        // onChange(false, content://media/external/video/media/328?blocking=1&orig_id=328&group_id=0)

        // TODO(b/123706961) Notify listener about changes
        // mChangeListener.xxx();
    }
}
