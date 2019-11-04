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
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.pump.util.Clog;
import com.android.pump.util.Collections;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

@WorkerThread
class AudioStore extends ContentObserver {
    private static final String TAG = Clog.tag(AudioStore.class);

    private final ContentResolver mContentResolver;
    private final ChangeListener mChangeListener;
    private final MediaProvider mMediaProvider;

    interface ChangeListener {
        void onAudiosAdded(@NonNull Collection<Audio> audios);
        void onArtistsAdded(@NonNull Collection<Artist> artists);
        void onAlbumsAdded(@NonNull Collection<Album> albums);
        void onGenresAdded(@NonNull Collection<Genre> genres);
        void onPlaylistsAdded(@NonNull Collection<Playlist> playlists);
    }

    @AnyThread
    AudioStore(@NonNull ContentResolver contentResolver, @NonNull ChangeListener changeListener,
            @NonNull MediaProvider mediaProvider) {
        super(null);

        Clog.i(TAG, "AudioStore(" + contentResolver + ", " + changeListener
                + ", " + mediaProvider + ")");
        mContentResolver = contentResolver;
        mChangeListener = changeListener;
        mMediaProvider = mediaProvider;

        // TODO(123705758) Do we need content observer for other content uris? (E.g. album, artist)
        mContentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true, this);

        // TODO(123705758) When to call unregisterContentObserver?
        // mContentResolver.unregisterContentObserver(this);
    }

    void load() {
        Clog.i(TAG, "load()");
        ArrayList<Artist> artists = new ArrayList<>();
        ArrayList<Album> albums = new ArrayList<>();
        ArrayList<Audio> audios = new ArrayList<>();
        ArrayList<Playlist> playlists = new ArrayList<>();
        ArrayList<Genre> genres = new ArrayList<>();

        // #1 Load artists
        {
            Uri contentUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Artists._ID
            };
            String sortOrder = MediaStore.Audio.Artists._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, null, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID);

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);

                        Artist artist = new Artist(id);
                        artists.add(artist);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        // #2 Load albums and connect each to artist
        {
            Uri contentUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Media.ARTIST_ID // TODO MediaStore.Audio.Albums.ARTIST_ID
            };
            String sortOrder = MediaStore.Audio.Albums._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, null, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID);
                    int artistIdColumn = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.ARTIST_ID); // TODO MediaStore.Audio.Albums.ARTIST_ID

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);

                        Album album = new Album(id);
                        albums.add(album);

                        if (!cursor.isNull(artistIdColumn)) {
                            long artistId = cursor.getLong(artistIdColumn);

                            Artist artist = Collections.find(artists, artistId, Artist::getId);
                            album.setArtist(artist);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        // #3 Load songs and connect each to album and artist
        {
            Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ID
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            String sortOrder = MediaStore.Audio.Media._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, selection, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
                    int artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
                    int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String mimeType = cursor.getString(mimeTypeColumn);

                        Audio audio = new Audio(id, mimeType);
                        audios.add(audio);

                        if (!cursor.isNull(artistIdColumn)) {
                            long artistId = cursor.getLong(artistIdColumn);

                            Artist artist = Collections.find(artists, artistId, Artist::getId);
                            audio.setArtist(artist);
                            artist.addAudio(audio);
                        }
                        if (!cursor.isNull(albumIdColumn)) {
                            long albumId = cursor.getLong(albumIdColumn);

                            Album album = Collections.find(albums, albumId, Album::getId);
                            audio.setAlbum(album);
                            album.addAudio(audio);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        // #4 Load playlists (optional?)
        {
            Uri contentUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Playlists._ID
            };
            String sortOrder = MediaStore.Audio.Playlists._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, null, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);

                        Playlist playlist = new Playlist(id);
                        playlists.add(playlist);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        // #5 Load genres (optional?)
        {
            Uri contentUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Genres._ID
            };
            String sortOrder = MediaStore.Audio.Genres._ID;
            Cursor cursor = mContentResolver.query(contentUri, projection, null, null, sortOrder);
            if (cursor != null) {
                try {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID);

                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);

                        Genre genre = new Genre(id);
                        genres.add(genre);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        mChangeListener.onAudiosAdded(audios);
        mChangeListener.onArtistsAdded(artists);
        mChangeListener.onAlbumsAdded(albums);
        mChangeListener.onGenresAdded(genres);
        mChangeListener.onPlaylistsAdded(playlists);
    }

    boolean loadData(@NonNull Audio audio) {
        boolean updated = false;

        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM_ID
        };
        String selection = MediaStore.Audio.Media._ID + " = ?";
        String[] selectionArgs = { Long.toString(audio.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(titleColumn)) {
                        String title = cursor.getString(titleColumn);
                        updated |= audio.setTitle(title);
                    }
                    if (!cursor.isNull(artistIdColumn)) {
                        long artistId = cursor.getLong(artistIdColumn);
                        Artist artist = mMediaProvider.getArtistById(artistId);
                        updated |= audio.setArtist(artist);
                        updated |= loadData(artist); // TODO(b/123707561) Load separate from audio
                    }
                    if (!cursor.isNull(albumIdColumn)) {
                        long albumId = cursor.getLong(albumIdColumn);
                        Album album = mMediaProvider.getAlbumById(albumId);
                        updated |= audio.setAlbum(album);
                        updated |= loadData(album); // TODO(b/123707561) Load separate from audio
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return updated;
    }

    boolean loadData(@NonNull Artist artist) {
        boolean updated = false;

        Uri contentUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Audio.Artists.ARTIST };
        String selection = MediaStore.Audio.Artists._ID + " = ?";
        String[] selectionArgs = { Long.toString(artist.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(artistColumn)) {
                        String name = cursor.getString(artistColumn);
                        updated |= artist.setName(name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        updated |= loadAlbums(artist); // TODO(b/123707561) Load separate from artist

        return updated;
    }

    boolean loadData(@NonNull Album album) {
        boolean updated = false;

        Uri contentUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Audio.Albums.ALBUM_ART,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Media.ARTIST_ID // TODO MediaStore.Audio.Albums.ARTIST_ID
        };
        String selection = MediaStore.Audio.Albums._ID + " = ?";
        String[] selectionArgs = { Long.toString(album.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int albumArtColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM_ART);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
                int artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID); // TODO MediaStore.Audio.Albums.ARTIST_ID

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(albumColumn)) {
                        String albumTitle = cursor.getString(albumColumn);
                        updated |= album.setTitle(albumTitle);
                    }
                    if (!cursor.isNull(albumArtColumn)) {
                        Uri albumArtUri = Uri.fromFile(new File(cursor.getString(albumArtColumn)));
                        updated |= album.setAlbumArtUri(albumArtUri);
                    }
                    if (!cursor.isNull(artistIdColumn)) {
                        long artistId = cursor.getLong(artistIdColumn);
                        Artist artist = mMediaProvider.getArtistById(artistId);
                        updated |= album.setArtist(artist);
                        updated |= loadData(artist); // TODO(b/123707561) Load separate from album
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return updated;
    }

    boolean loadData(@NonNull Genre genre) {
        boolean updated = false;

        Uri contentUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Audio.Genres.NAME };
        String selection = MediaStore.Audio.Genres._ID + " = ?";
        String[] selectionArgs = { Long.toString(genre.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(nameColumn)) {
                        String name = cursor.getString(nameColumn);
                        updated |= genre.setName(name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        updated |= loadAudios(genre); // TODO(b/123707561) Load separate from genre

        return updated;
    }

    boolean loadData(@NonNull Playlist playlist) {
        boolean updated = false;

        Uri contentUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Audio.Playlists.NAME };
        String selection = MediaStore.Audio.Playlists._ID + " = ?";
        String[] selectionArgs = { Long.toString(playlist.getId()) };
        Cursor cursor = mContentResolver.query(
                contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);

                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(nameColumn)) {
                        String name = cursor.getString(nameColumn);
                        updated |= playlist.setName(name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        updated |= loadAudios(playlist); // TODO(b/123707561) Load separate from playlist

        return updated;
    }

    boolean loadAlbums(@NonNull Artist artist) {
        boolean updated = false;

        // TODO Remove hardcoded value
        Uri contentUri = MediaStore.Audio.Artists.Albums.getContentUri("external", artist.getId());
        /*
         * On some devices MediaStore doesn't use ALBUM_ID as key from Artist to Album, but rather
         * _ID. In order to support these devices we don't pass a projection, to avoid the
         * IllegalArgumentException(Invalid column) exception, and then resort to _ID.
         */
        String[] projection = null; // { MediaStore.Audio.Artists.Albums.ALBUM_ID };
        Cursor cursor = mContentResolver.query(contentUri, projection, null, null, null);
        if (cursor != null) {
            try {
                int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Artists.Albums.ALBUM_ID);
                if (albumIdColumn < 0) {
                    // On some devices the ALBUM_ID column doesn't exist and _ID is used instead.
                    albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }

                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    long albumId = cursor.getLong(albumIdColumn);
                    Album album = mMediaProvider.getAlbumById(albumId);
                    updated |= artist.addAlbum(album);
                    //updated |= loadData(album); // TODO(b/123707561) Load separate from artist
                }
            } finally {
                cursor.close();
            }
        }

        return updated;
    }

    boolean loadAudios(@NonNull Genre genre) {
        boolean updated = false;

        // TODO Remove hardcoded value
        Uri contentUri = MediaStore.Audio.Genres.Members.getContentUri("external", genre.getId());
        String[] projection = { MediaStore.Audio.Genres.Members.AUDIO_ID };
        Cursor cursor = mContentResolver.query(contentUri, projection, null, null, null);
        if (cursor != null) {
            try {
                int audioIdColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Genres.Members.AUDIO_ID);

                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    long audioId = cursor.getLong(audioIdColumn);
                    Audio audio = mMediaProvider.getAudioById(audioId);
                    updated |= genre.addAudio(audio);
                    updated |= loadData(audio); // TODO(b/123707561) Load separate from genre
                }
            } finally {
                cursor.close();
            }
        }

        return updated;
    }

    boolean loadAudios(@NonNull Playlist playlist) {
        boolean updated = false;

        // TODO Remove hardcoded value
        Uri contentUri = MediaStore.Audio.Playlists.Members.getContentUri(
                "external", playlist.getId());
        String[] projection = { MediaStore.Audio.Playlists.Members.AUDIO_ID };
        Cursor cursor = mContentResolver.query(contentUri, projection, null, null, null);
        if (cursor != null) {
            try {
                int audioIdColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID);

                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    long audioId = cursor.getLong(audioIdColumn);
                    Audio audio = mMediaProvider.getAudioById(audioId);
                    updated |= playlist.addAudio(audio);
                    updated |= loadData(audio); // TODO(b/123707561) Load separate from playlist
                }
            } finally {
                cursor.close();
            }
        }

        return updated;
    }

    @Override
    public void onChange(boolean selfChange) {
        Clog.i(TAG, "onChange(" + selfChange + ")");
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        Clog.i(TAG, "onChange(" + selfChange + ", " + uri + ")");
        // TODO(123705758) Figure out what changed
        // onChange(false, content://media)
        // onChange(false, content://media/external)
        // onChange(false, content://media/external/audio/media/444)
        // onChange(false, content://media/external/video/media/328?blocking=1&orig_id=328&group_id=0)

        // TODO(123705758) Notify listener about changes
        // mChangeListener.xxx();
    }

    // TODO Remove unused methods
    private long createPlaylist(@NonNull String name) {
        Clog.i(TAG, "createPlaylist(" + name + ")");
        Uri contentUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(MediaStore.Audio.Playlists.NAME, name);
        Uri uri = mContentResolver.insert(contentUri, contentValues);
        return Long.parseLong(uri.getLastPathSegment());
    }

    private void addToPlaylist(@NonNull Playlist playlist, @NonNull Audio audio) {
        Clog.i(TAG, "addToPlaylist(" + playlist + ", " + audio + ")");
        long base = getLastPlayOrder(playlist);

        // TODO Remove hardcoded value
        Uri contentUri = MediaStore.Audio.Playlists.Members.getContentUri(
                "external", playlist.getId());
        ContentValues contentValues = new ContentValues(2);
        contentValues.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audio.getId());
        contentValues.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + 1);
        mContentResolver.insert(contentUri, contentValues);
    }

    private long getLastPlayOrder(@NonNull Playlist playlist) {
        Clog.i(TAG, "getLastPlayOrder(" + playlist + ")");

        long playOrder = -1;

        // TODO Remove hardcoded value
        Uri contentUri = MediaStore.Audio.Playlists.Members.getContentUri(
                "external", playlist.getId());
        String[] projection = { MediaStore.Audio.Playlists.Members.PLAY_ORDER };
        String sortOrder = MediaStore.Audio.Playlists.Members.PLAY_ORDER + " DESC LIMIT 1";
        Cursor cursor = mContentResolver.query(
                contentUri, projection, null, null, sortOrder);
        if (cursor != null) {
            try {
                int playOrderColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER);

                if (cursor.moveToFirst()) {
                    playOrder = cursor.getLong(playOrderColumn);
                }
            } finally {
                cursor.close();
            }
        }

        return playOrder;
    }
}
