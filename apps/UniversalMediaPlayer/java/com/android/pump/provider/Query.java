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

package com.android.pump.provider;

import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.android.pump.util.Clog;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AnyThread
public final class Query {
    private static final String TAG = Clog.tag(Query.class);

    private static final Pattern PATTERN_EPISODE = Pattern.compile(
            "([^\\\\/]+?)(?:\\(((?:19|20)\\d{2})\\))?\\s*" +
            "[Ss](\\d{1,2})[._x]?[Ee](\\d{1,2})(?!.*[Ss]\\d{1,2}[._x]?[Ee]\\d{1,2})");
    private static final Pattern PATTERN_MOVIE = Pattern.compile(
            "([^\\\\/]+)\\(((?:19|20)\\d{2})\\)(?!.*\\((?:19|20)\\d{2}\\))");
    private static final Pattern PATTERN_CLEANUP = Pattern.compile("\\s+");

    private final String mName;
    private final int mYear;
    private final int mSeason;
    private final int mEpisode;

    private Query(String name) {
        //Clog.i(TAG, "Query(" + name + ")");
        mName = name;
        mYear = -1;
        mSeason = -1;
        mEpisode = -1;
    }

    private Query(String name, int year) {
        //Clog.i(TAG, "Query(" + name + ", " + year + ")");
        mName = name;
        mYear = year;
        mSeason = -1;
        mEpisode = -1;
    }

    private Query(String name, int season, int episode) {
        //Clog.i(TAG, "Query(" + name + ", " + season + ", " + episode + ")");
        mName = name;
        mYear = -1;
        mSeason = season;
        mEpisode = episode;
    }

    private Query(String name, int year, int season, int episode) {
        //Clog.i(TAG, "Query(" + name + ", " + year + ", " + season + ", " + episode + ")");
        mName = name;
        mYear = year;
        mSeason = season;
        mEpisode = episode;
    }

    public boolean isMovie() {
        return hasYear() && !isEpisode();
    }

    public boolean isEpisode() {
        return mSeason >= 0 && mEpisode >= 0;
    }

    public @NonNull String getName() {
        return mName;
    }

    public boolean hasYear() {
        return mYear >= 0;
    }

    public int getYear() {
        return mYear;
    }

    public int getSeason() {
        return mSeason;
    }

    public int getEpisode() {
        return mEpisode;
    }

    public static @NonNull Query parse(@NonNull Uri uri) {
        //Clog.i(TAG, "parse(" + uri + ")");
        String filePath = uri.getPath();
        Query query;
        query = parseEpisode(filePath);
        if (query == null) {
            query = parseMovie(filePath);
        }
        if (query == null) {
            query = new Query(uri.getLastPathSegment());
        }
        return query;
    }

    private static Query parseEpisode(String filePath) {
        //Clog.i(TAG, "parseEpisode(" + filePath + ")");
        Matcher matcher = PATTERN_EPISODE.matcher(filePath);
        if (matcher.find()) {
            MatchResult matchResult = matcher.toMatchResult();
            if (matchResult.groupCount() == 4) {
                String name = cleanup(matchResult.group(1));
                int year = matchResult.group(2) == null ? 0 : Integer.valueOf(matchResult.group(2));
                int season = Integer.valueOf(matchResult.group(3));
                int episode = Integer.valueOf(matchResult.group(4));
                //Clog.i(TAG, "name = " + name);
                //if (year > 0) {
                //    Clog.i(TAG, "year = " + year);
                //}
                //Clog.i(TAG, "season = " + season);
                //Clog.i(TAG, "episode = " + episode);
                if (year > 0) {
                    return new Query(name, year, season, episode);
                } else {
                    return new Query(name, season, episode);
                }
            }
        }
        return null;
    }

    private static Query parseMovie(String filePath) {
        //Clog.i(TAG, "parseMovie(" + filePath + ")");
        Matcher matcher = PATTERN_MOVIE.matcher(filePath);
        if (matcher.find()) {
            MatchResult matchResult = matcher.toMatchResult();
            if (matchResult.groupCount() == 2) {
                String name = cleanup(matchResult.group(1));
                int year = Integer.valueOf(matchResult.group(2));
                //Clog.i(TAG, "name = " + name);
                //Clog.i(TAG, "year = " + year);
                return new Query(name, year);
            }
        }
        return null;
    }

    private static String cleanup(String string) {
        return PATTERN_CLEANUP.matcher(string).replaceAll(" ").trim();
    }
}
