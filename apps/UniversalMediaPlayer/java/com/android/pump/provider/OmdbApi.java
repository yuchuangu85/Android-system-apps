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
import androidx.annotation.WorkerThread;

import com.android.pump.db.Album;
import com.android.pump.db.Artist;
import com.android.pump.db.DataProvider;
import com.android.pump.db.Episode;
import com.android.pump.db.Movie;
import com.android.pump.db.Series;
import com.android.pump.util.Clog;
import com.android.pump.util.Http;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@WorkerThread
public final class OmdbApi implements DataProvider {
    private static final String TAG = Clog.tag(OmdbApi.class);

    private static final DataProvider INSTANCE = new OmdbApi();

    private OmdbApi() { }

    @AnyThread
    public static @NonNull DataProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean populateArtist(@NonNull Artist artist) throws IOException {
        // NO-OP
        return false;
    }

    @Override
    public boolean populateAlbum(@NonNull Album album) throws IOException {
        // NO-OP
        return false;
    }

    @Override
    public boolean populateMovie(@NonNull Movie movie) throws IOException {
        boolean updated = false;
        try {
            JSONObject root = (JSONObject) getContent(getContentUri(movie));
            updated |= movie.setPosterUri(getPosterUri(root.getString("imdbID")));
            updated |= movie.setSynopsis(root.getString("Plot"));
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse search result", e);
            throw new IOException(e);
        }
        return updated;
    }

    @Override
    public boolean populateSeries(@NonNull Series series) throws IOException {
        boolean updated = false;
        try {
            JSONObject root = (JSONObject) getContent(getContentUri(series));
            updated |= series.setPosterUri(getPosterUri(root.getString("imdbID")));
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse search result", e);
            throw new IOException(e);
        }
        return updated;
    }

    @Override
    public boolean populateEpisode(@NonNull Episode episode) throws IOException {
        boolean updated = false;
        try {
            JSONObject root = (JSONObject) getContent(getContentUri(episode));
            updated |= episode.setPosterUri(getPosterUri(root.getString("imdbID")));
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse search result", e);
            throw new IOException(e);
        }
        return updated;
    }

    private static @NonNull Uri getContentUri(@NonNull Movie movie) {
        Uri.Builder ub = getContentUri(movie.getTitle());
        if (movie.hasYear()) {
            ub.appendQueryParameter("y", Integer.toString(movie.getYear()));
        }
        ub.appendQueryParameter("type", "movie");
        return ub.build();
    }

    private static @NonNull Uri getContentUri(@NonNull Series series) {
        Uri.Builder ub = getContentUri(series.getTitle());
        if (series.hasYear()) {
            ub.appendQueryParameter("y", Integer.toString(series.getYear()));
        }
        ub.appendQueryParameter("type", "series");
        return ub.build();
    }

    private static @NonNull Uri getContentUri(@NonNull Episode episode) {
        Series series = episode.getSeries();
        Uri.Builder ub = getContentUri(series.getTitle());
        if (series.hasYear()) {
            ub.appendQueryParameter("y", Integer.toString(series.getYear()));
        }
        ub.appendQueryParameter("Season", Integer.toString(episode.getSeason()));
        ub.appendQueryParameter("Episode", Integer.toString(episode.getEpisode()));
        ub.appendQueryParameter("type", "episode");
        return ub.build();
    }

    private static @NonNull Uri.Builder getContentUri(@NonNull String title) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("omdbapi.com");
        ub.appendQueryParameter("apikey", ApiKeys.OMDB_API);
        ub.appendQueryParameter("t", title);
        return ub;
    }

    private static @NonNull Object getContent(@NonNull Uri uri) throws IOException, JSONException {
        return new JSONTokener(new String(Http.get(uri.toString()), StandardCharsets.UTF_8))
                .nextValue();
    }

    private static @NonNull Uri getPosterUri(@NonNull String imdbId) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("img.omdbapi.com");
        ub.appendQueryParameter("apikey", ApiKeys.OMDB_API);
        ub.appendQueryParameter("i", imdbId);
        return ub.build();
    }
}
