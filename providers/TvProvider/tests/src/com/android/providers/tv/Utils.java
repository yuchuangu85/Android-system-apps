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

package com.android.providers.tv;

import static junit.framework.Assert.assertNotNull;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.RecordedPrograms;
import android.net.Uri;

import com.google.android.collect.Sets;

import java.util.Objects;
import java.util.Set;

public class Utils {
    private static final String FAKE_INPUT_ID = "PackageRemovedReceiverTest";
    private static final String FAKE_SESSION_TOKEN = "fakeSessionToken";

    public static void clearTvProvider(ContentResolver resolver) {
        resolver.delete(Channels.CONTENT_URI, null, null);
        // Programs, PreviewPrograms, and WatchedPrograms table will be automatically cleared when
        // the Channels table is cleared.
        resolver.delete(TvContract.RecordedPrograms.CONTENT_URI, null, null);
        resolver.delete(TvContract.WatchNextPrograms.CONTENT_URI, null, null);
    }

    private static class BaseProgram {
        long id;
        final String packageName;

        BaseProgram(String pkgName) {
            this(-1, pkgName);
        }

        BaseProgram(long id, String pkgName) {
            this.id = id;
            this.packageName = pkgName;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BaseProgram)) {
                return false;
            }
            BaseProgram that = (BaseProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, packageName);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(id=" + id + ",packageName=" + packageName + ")";
        }
    }

    public static class Program extends BaseProgram {
        Program(String pkgName) {
            super(-1, pkgName);
        }

        Program(long id, String pkgName) {
            super(id, pkgName);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Program)) {
                return false;
            }
            Program that = (Program) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }
    }

    public static class PreviewProgram extends BaseProgram {
        PreviewProgram(String pkgName) {
            super(-1, pkgName);
        }

        PreviewProgram(long id, String pkgName) {
            super(id, pkgName);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PreviewProgram)) {
                return false;
            }
            PreviewProgram that = (PreviewProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }
    }

    public static class RecordedProgram extends BaseProgram {
        Long channelId;
        RecordedProgram(String pkgName, Long channelId) {
            this(-1, pkgName, channelId);
        }

        RecordedProgram(long id, String pkgName, Long channelId) {
            super(id, pkgName);
            this.channelId = channelId;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RecordedProgram)) {
                return false;
            }
            RecordedProgram that = (RecordedProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName)
                    && Objects.equals(channelId, that.channelId);
        }

        @Override
        public String toString() {
            return "RecordedProgram(id=" + id + ",packageName=" + packageName + ",channelId="
                    + channelId + ")";
        }
    }

    public static class WatchedProgram extends BaseProgram {
        WatchedProgram(String pkgName) {
            super(-1, pkgName);
        }

        WatchedProgram(long id, String pkgName) {
            super(id, pkgName);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WatchedProgram)) {
                return false;
            }
            WatchedProgram that = (WatchedProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }
    }

    public static class WatchNextProgram extends BaseProgram {
        WatchNextProgram(String pkgName) {
            super(-1, pkgName);
        }

        WatchNextProgram(long id, String pkgName) {
            super(id, pkgName);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WatchNextProgram)) {
                return false;
            }
            WatchNextProgram that = (WatchNextProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }
    }

    public static long insertChannel(ContentResolver resolver) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        Uri uri = resolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    public static void insertPrograms(
            ContentResolver resolver, long channelId, Program... programs) {
        ContentValues values = new ContentValues();
        values.put(TvContract.Programs.COLUMN_CHANNEL_ID, channelId);
        for (Program program : programs) {
            Uri uri = resolver.insert(TvContract.Programs.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    public static Set<Program> queryPrograms(ContentResolver resolver) {
        String[] projection = new String[] {
                TvContract.Programs._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        };

        Cursor cursor =
                resolver.query(TvContract.Programs.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<Program> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new Program(cursor.getLong(0), cursor.getString(1)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    public static void insertPreviewPrograms(
            ContentResolver resolver, long channelId, PreviewProgram... programs) {
        ContentValues values = new ContentValues();
        values.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId);
        values.put(
                TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_TV_EPISODE);
        for (PreviewProgram program : programs) {
            Uri uri = resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    public static Set<PreviewProgram> queryPreviewPrograms(ContentResolver resolver) {
        String[] projection = new String[] {
                TvContract.PreviewPrograms._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        };

        Cursor cursor =
                resolver.query(
                        TvContract.PreviewPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<PreviewProgram> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new PreviewProgram(cursor.getLong(0), cursor.getString(1)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    public static void insertRecordedPrograms(
            ContentResolver resolver, long channelId, RecordedProgram... programs) {
        ContentValues values = new ContentValues();
        values.put(RecordedPrograms.COLUMN_CHANNEL_ID, channelId);
        values.put(RecordedPrograms.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        for (RecordedProgram program : programs) {
            Uri uri = resolver.insert(RecordedPrograms.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    public static Set<RecordedProgram> queryRecordedPrograms(ContentResolver resolver) {
        String[] projection = new String[] {
                RecordedPrograms._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
                RecordedPrograms.COLUMN_CHANNEL_ID,
        };

        Cursor cursor = resolver.query(RecordedPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<RecordedProgram> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                Long channelId = cursor.isNull(2) ? null : cursor.getLong(2);
                programs.add(
                        new RecordedProgram(cursor.getLong(0), cursor.getString(1), channelId));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    public static void insertWatchedPrograms(ContentResolver resolver, String packageName,
            long channelId, WatchedProgram... programs) {
        ContentValues values = new ContentValues();
        values.put(TvContract.WatchedPrograms.COLUMN_PACKAGE_NAME, packageName);
        values.put(TvContract.WatchedPrograms.COLUMN_CHANNEL_ID, channelId);
        values.put(TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS, 1000);
        values.put(TvContract.WatchedPrograms.COLUMN_INTERNAL_SESSION_TOKEN, FAKE_SESSION_TOKEN);
        values.put(TvProvider.WATCHED_PROGRAMS_COLUMN_CONSOLIDATED, 1);
        for (WatchedProgram program : programs) {
            Uri uri = resolver.insert(TvContract.WatchedPrograms.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    public static Set<WatchedProgram> queryWatchedPrograms(ContentResolver resolver) {
        String[] projection = new String[] {
                TvContract.WatchedPrograms._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        };

        Cursor cursor =
                resolver.query(
                        TvContract.WatchedPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<WatchedProgram> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new WatchedProgram(cursor.getLong(0), cursor.getString(1)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    public static void insertWatchNextPrograms(ContentResolver resolver, String packageName,
            WatchNextProgram... programs) {
        ContentValues values = new ContentValues();
        values.put(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, packageName);
        values.put(TvContract.WatchNextPrograms.COLUMN_TYPE,
                TvContract.WatchNextPrograms.TYPE_TV_EPISODE);
        for (WatchNextProgram program : programs) {
            Uri uri = resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    public static Set<WatchNextProgram> queryWatchNextPrograms(ContentResolver resolver) {
        String[] projection = new String[] {
                TvContract.WatchNextPrograms._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        };

        Cursor cursor =
                resolver.query(
                        TvContract.WatchNextPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<WatchNextProgram> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new WatchNextProgram(cursor.getLong(0), cursor.getString(1)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    public static long getChannelCount(ContentResolver resolver) {
        String[] projection = new String[] {
                Channels._ID,
        };

        Cursor cursor = resolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public static BroadcastReceiver.PendingResult createFakePendingResultForTests() {
        return new BroadcastReceiver.PendingResult(0, null, null, 0, true, false, null, 0, 0);
    }

    private Utils() { }
}
