/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.dvr.data;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract.Programs.Genres;
import android.media.tv.TvContract.RecordedPrograms;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;
import com.android.tv.common.R;
import com.android.tv.common.TvContentRatingCache;
import com.android.tv.common.data.RecordedProgramState;
import com.android.tv.common.util.CommonUtils;
import com.android.tv.common.util.StringUtils;
import com.android.tv.data.BaseProgram;
import com.android.tv.data.GenreItems;
import com.android.tv.data.InternalDataUtils;
import com.android.tv.util.TvProviderUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/** Immutable instance of {@link android.media.tv.TvContract.RecordedPrograms}. */
@TargetApi(Build.VERSION_CODES.N)
@AutoValue
public abstract class RecordedProgram extends BaseProgram {
    public static final int ID_NOT_SET = -1;
    private static final String TAG = "RecordedProgram";

    public static final String[] PROJECTION = {
        RecordedPrograms._ID,
        RecordedPrograms.COLUMN_PACKAGE_NAME,
        RecordedPrograms.COLUMN_INPUT_ID,
        RecordedPrograms.COLUMN_CHANNEL_ID,
        RecordedPrograms.COLUMN_TITLE,
        RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        RecordedPrograms.COLUMN_SEASON_TITLE,
        RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        RecordedPrograms.COLUMN_EPISODE_TITLE,
        RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
        RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS,
        RecordedPrograms.COLUMN_BROADCAST_GENRE,
        RecordedPrograms.COLUMN_CANONICAL_GENRE,
        RecordedPrograms.COLUMN_SHORT_DESCRIPTION,
        RecordedPrograms.COLUMN_LONG_DESCRIPTION,
        RecordedPrograms.COLUMN_VIDEO_WIDTH,
        RecordedPrograms.COLUMN_VIDEO_HEIGHT,
        RecordedPrograms.COLUMN_AUDIO_LANGUAGE,
        RecordedPrograms.COLUMN_CONTENT_RATING,
        RecordedPrograms.COLUMN_POSTER_ART_URI,
        RecordedPrograms.COLUMN_THUMBNAIL_URI,
        RecordedPrograms.COLUMN_SEARCHABLE,
        RecordedPrograms.COLUMN_RECORDING_DATA_URI,
        RecordedPrograms.COLUMN_RECORDING_DATA_BYTES,
        RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
        RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
        RecordedPrograms.COLUMN_VERSION_NUMBER,
        RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
    };

    public static RecordedProgram fromCursor(Cursor cursor) {
        int index = 0;
        Builder builder =
                builder()
                        .setId(cursor.getLong(index++))
                        .setPackageName(cursor.getString(index++))
                        .setInputId(cursor.getString(index++))
                        .setChannelId(cursor.getLong(index++))
                        .setTitle(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setSeasonNumber(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setSeasonTitle(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setEpisodeNumber(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setEpisodeTitle(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setStartTimeUtcMillis(cursor.getLong(index++))
                        .setEndTimeUtcMillis(cursor.getLong(index++))
                        .setBroadcastGenres(cursor.getString(index++))
                        .setCanonicalGenres(cursor.getString(index++))
                        .setDescription(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setLongDescription(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setVideoWidth(cursor.getInt(index++))
                        .setVideoHeight(cursor.getInt(index++))
                        .setAudioLanguage(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setContentRatings(
                                TvContentRatingCache.getInstance()
                                        .getRatings(cursor.getString(index++)))
                        .setPosterArtUri(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setThumbnailUri(StringUtils.nullToEmpty(cursor.getString(index++)))
                        .setSearchable(cursor.getInt(index++) == 1)
                        .setDataUri(cursor.getString(index++))
                        .setDataBytes(cursor.getLong(index++))
                        .setDurationMillis(cursor.getLong(index++))
                        .setExpireTimeUtcMillis(cursor.getLong(index++))
                        .setVersionNumber(cursor.getInt(index++));
        if (CommonUtils.isInBundledPackageSet(builder.getPackageName())) {
            InternalDataUtils.deserializeInternalProviderData(cursor.getBlob(index), builder);
        }
        index++;
        if (TvProviderUtils.getRecordedProgramHasSeriesIdColumn()) {
            builder.setSeriesId(StringUtils.nullToEmpty(cursor.getString(index++)));
        }
        if (TvProviderUtils.getRecordedProgramHasStateColumn()) {
            builder.setState(cursor.getString(index++));
        }
        return builder.build();
    }

    @WorkerThread
    public static ContentValues toValues(Context context, RecordedProgram recordedProgram) {
        ContentValues values = new ContentValues();
        if (recordedProgram.getId() != ID_NOT_SET) {
            values.put(RecordedPrograms._ID, recordedProgram.getId());
        }
        values.put(RecordedPrograms.COLUMN_INPUT_ID, recordedProgram.getInputId());
        values.put(RecordedPrograms.COLUMN_CHANNEL_ID, recordedProgram.getChannelId());
        values.put(RecordedPrograms.COLUMN_TITLE, recordedProgram.getTitle());
        values.put(
                RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER, recordedProgram.getSeasonNumber());
        values.put(RecordedPrograms.COLUMN_SEASON_TITLE, recordedProgram.getSeasonTitle());
        values.put(
                RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, recordedProgram.getEpisodeNumber());
        values.put(RecordedPrograms.COLUMN_EPISODE_TITLE, recordedProgram.getEpisodeTitle());
        values.put(
                RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
                recordedProgram.getStartTimeUtcMillis());
        values.put(
                RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, recordedProgram.getEndTimeUtcMillis());
        values.put(
                RecordedPrograms.COLUMN_BROADCAST_GENRE,
                safeEncode(recordedProgram.getBroadcastGenres()));
        values.put(
                RecordedPrograms.COLUMN_CANONICAL_GENRE,
                safeEncode(recordedProgram.getCanonicalGenres()));
        values.put(RecordedPrograms.COLUMN_SHORT_DESCRIPTION, recordedProgram.getDescription());
        values.put(RecordedPrograms.COLUMN_LONG_DESCRIPTION, recordedProgram.getLongDescription());
        if (recordedProgram.getVideoWidth() == 0) {
            values.putNull(RecordedPrograms.COLUMN_VIDEO_WIDTH);
        } else {
            values.put(RecordedPrograms.COLUMN_VIDEO_WIDTH, recordedProgram.getVideoWidth());
        }
        if (recordedProgram.getVideoHeight() == 0) {
            values.putNull(RecordedPrograms.COLUMN_VIDEO_HEIGHT);
        } else {
            values.put(RecordedPrograms.COLUMN_VIDEO_HEIGHT, recordedProgram.getVideoHeight());
        }
        values.put(RecordedPrograms.COLUMN_AUDIO_LANGUAGE, recordedProgram.getAudioLanguage());
        values.put(
                RecordedPrograms.COLUMN_CONTENT_RATING,
                TvContentRatingCache.contentRatingsToString(recordedProgram.getContentRatings()));
        values.put(RecordedPrograms.COLUMN_POSTER_ART_URI, recordedProgram.getPosterArtUri());
        values.put(RecordedPrograms.COLUMN_THUMBNAIL_URI, recordedProgram.getThumbnailUri());
        values.put(RecordedPrograms.COLUMN_SEARCHABLE, recordedProgram.isSearchable() ? 1 : 0);
        values.put(
                RecordedPrograms.COLUMN_RECORDING_DATA_URI,
                safeToString(recordedProgram.getDataUri()));
        values.put(RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, recordedProgram.getDataBytes());
        values.put(
                RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
                recordedProgram.getDurationMillis());
        values.put(
                RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
                recordedProgram.getExpireTimeUtcMillis());
        values.put(
                RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
                InternalDataUtils.serializeInternalProviderData(recordedProgram));
        values.put(RecordedPrograms.COLUMN_VERSION_NUMBER, recordedProgram.getVersionNumber());
        if (TvProviderUtils.checkSeriesIdColumn(context, RecordedPrograms.CONTENT_URI)) {
            values.put(COLUMN_SERIES_ID, recordedProgram.getSeriesId());
        }
        if (TvProviderUtils.checkStateColumn(context, RecordedPrograms.CONTENT_URI)) {
            values.put(COLUMN_STATE, recordedProgram.getState().toString());
        }
        return values;
    }

    /** Builder for {@link RecordedProgram}s. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setId(long id);

        public abstract Builder setPackageName(String packageName);

        abstract String getPackageName();

        public abstract Builder setInputId(String inputId);

        public abstract Builder setChannelId(long channelId);

        abstract String getTitle();

        public abstract Builder setTitle(String title);

        abstract String getSeriesId();

        public abstract Builder setSeriesId(String seriesId);

        public abstract Builder setSeasonNumber(String seasonNumber);

        public abstract Builder setSeasonTitle(String seasonTitle);

        @Nullable
        abstract String getEpisodeNumber();

        public abstract Builder setEpisodeNumber(String episodeNumber);

        public abstract Builder setEpisodeTitle(String episodeTitle);

        public abstract Builder setStartTimeUtcMillis(long startTimeUtcMillis);

        public abstract Builder setEndTimeUtcMillis(long endTimeUtcMillis);

        public abstract Builder setState(RecordedProgramState state);

        public Builder setState(@Nullable String state) {

            if (!TextUtils.isEmpty(state)) {
                try {
                    return setState(RecordedProgramState.valueOf(state));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Unknown recording state " + state, e);
                }
            }
            return setState(RecordedProgramState.NOT_SET);
        }

        public Builder setBroadcastGenres(@Nullable String broadcastGenres) {
            return setBroadcastGenres(
                    TextUtils.isEmpty(broadcastGenres)
                            ? ImmutableList.of()
                            : ImmutableList.copyOf(Genres.decode(broadcastGenres)));
        }

        public abstract Builder setBroadcastGenres(ImmutableList<String> broadcastGenres);

        public Builder setCanonicalGenres(String canonicalGenres) {
            return setCanonicalGenres(
                    TextUtils.isEmpty(canonicalGenres)
                            ? ImmutableList.of()
                            : ImmutableList.copyOf(Genres.decode(canonicalGenres)));
        }

        public abstract Builder setCanonicalGenres(ImmutableList<String> canonicalGenres);

        public abstract Builder setDescription(String shortDescription);

        public abstract Builder setLongDescription(String longDescription);

        public abstract Builder setVideoWidth(int videoWidth);

        public abstract Builder setVideoHeight(int videoHeight);

        public abstract Builder setAudioLanguage(String audioLanguage);

        public abstract Builder setContentRatings(ImmutableList<TvContentRating> contentRatings);

        private Uri toUri(@Nullable String uriString) {
            try {
                return uriString == null ? null : Uri.parse(uriString);
            } catch (Exception e) {
                return Uri.EMPTY;
            }
        }

        public abstract Builder setPosterArtUri(String posterArtUri);

        public abstract Builder setThumbnailUri(String thumbnailUri);

        public abstract Builder setSearchable(boolean searchable);

        public Builder setDataUri(@Nullable String dataUri) {
            return setDataUri(toUri(dataUri));
        }

        public abstract Builder setDataUri(Uri dataUri);

        public abstract Builder setDataBytes(long dataBytes);

        public abstract Builder setDurationMillis(long durationMillis);

        public abstract Builder setExpireTimeUtcMillis(long expireTimeUtcMillis);

        public abstract Builder setVersionNumber(int versionNumber);

        abstract RecordedProgram autoBuild();

        public RecordedProgram build() {
            if (TextUtils.isEmpty(getTitle())) {
                // If title is null, series cannot be generated for this program.
                setSeriesId(null);
            } else if (TextUtils.isEmpty(getSeriesId()) && !TextUtils.isEmpty(getEpisodeNumber())) {
                // If series ID is not set, generate it for the episodic program of other TV input.
                setSeriesId(BaseProgram.generateSeriesId(getPackageName(), getTitle()));
            }
            return (autoBuild());
        }
    }

    public static Builder builder() {
        return new AutoValue_RecordedProgram.Builder()
                .setId(ID_NOT_SET)
                .setChannelId(ID_NOT_SET)
                .setAudioLanguage("")
                .setBroadcastGenres("")
                .setCanonicalGenres("")
                .setContentRatings(ImmutableList.of())
                .setDataUri("")
                .setDurationMillis(0)
                .setDescription("")
                .setDataBytes(0)
                .setLongDescription("")
                .setEndTimeUtcMillis(0)
                .setEpisodeNumber("")
                .setEpisodeTitle("")
                .setExpireTimeUtcMillis(0)
                .setPackageName("")
                .setPosterArtUri("")
                .setSeasonNumber("")
                .setSeasonTitle("")
                .setSearchable(false)
                .setSeriesId("")
                .setStartTimeUtcMillis(0)
                .setState(RecordedProgramState.NOT_SET)
                .setThumbnailUri("")
                .setTitle("")
                .setVersionNumber(0)
                .setVideoHeight(0)
                .setVideoWidth(0);
    }

    public static final Comparator<RecordedProgram> START_TIME_THEN_ID_COMPARATOR =
            (RecordedProgram lhs, RecordedProgram rhs) -> {
                int res = Long.compare(lhs.getStartTimeUtcMillis(), rhs.getStartTimeUtcMillis());
                if (res != 0) {
                    return res;
                }
                return Long.compare(lhs.getId(), rhs.getId());
            };

    private static final long CLIPPED_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5);

    public abstract String getAudioLanguage();

    public abstract ImmutableList<String> getBroadcastGenres();

    public abstract ImmutableList<String> getCanonicalGenres();

    /** Returns array of canonical genre ID's for this recorded program. */
    @Override
    public int[] getCanonicalGenreIds() {

        ImmutableList<String> canonicalGenres = getCanonicalGenres();
        int[] genreIds = new int[getCanonicalGenres().size()];
        for (int i = 0; i < canonicalGenres.size(); i++) {
            genreIds[i] = GenreItems.getId(canonicalGenres.get(i));
        }
        return genreIds;
    }

    public abstract Uri getDataUri();

    public abstract long getDataBytes();

    @Nullable
    public String getEpisodeDisplayNumber(Context context) {
        if (!TextUtils.isEmpty(getEpisodeNumber())) {
            if (TextUtils.equals(getSeasonNumber(), "0")) {
                // Do not show "S0: ".
                return context.getResources()
                        .getString(
                                R.string.display_episode_number_format_no_season_number,
                                getEpisodeNumber());
            } else {
                return context.getResources()
                        .getString(
                                R.string.display_episode_number_format,
                                getSeasonNumber(),
                                getEpisodeNumber());
            }
        }
        return null;
    }

    public abstract long getExpireTimeUtcMillis();

    public abstract String getPackageName();

    public abstract String getInputId();

    @Override
    public boolean isValid() {
        return true;
    }

    public boolean isVisible() {
        switch (getState()) {
            case NOT_SET:
            case FINISHED:
                return true;
            default:
                return false;
        }
    }

    public boolean isPartial() {
        return getState() == RecordedProgramState.PARTIAL;
    }

    public abstract boolean isSearchable();

    public abstract String getSeasonTitle();

    public abstract RecordedProgramState getState();

    public Uri getUri() {
        return ContentUris.withAppendedId(RecordedPrograms.CONTENT_URI, getId());
    }

    public abstract int getVersionNumber();

    public abstract int getVideoHeight();

    public abstract int getVideoWidth();

    /** Checks whether the recording has been clipped or not. */
    public boolean isClipped() {
        return getEndTimeUtcMillis() - getStartTimeUtcMillis() - getDurationMillis()
                > CLIPPED_THRESHOLD_MS;
    }

    public abstract Builder toBuilder();

    @CheckResult
    public RecordedProgram withId(long id) {
        return toBuilder().setId(id).build();
    }

    @Nullable
    private static String safeToString(@Nullable Object o) {
        return o == null ? null : o.toString();
    }

    @Nullable
    private static String safeEncode(@Nullable ImmutableList<String> genres) {
        return genres == null ? null : Genres.encode(genres.toArray(new String[0]));
    }

    /** Returns an array containing all of the elements in the list. */
    public static RecordedProgram[] toArray(Collection<RecordedProgram> recordedPrograms) {
        return recordedPrograms.toArray(new RecordedProgram[recordedPrograms.size()]);
    }
}
