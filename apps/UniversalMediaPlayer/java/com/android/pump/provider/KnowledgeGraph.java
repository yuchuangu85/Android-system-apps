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
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.pump.db.Album;
import com.android.pump.db.Artist;
import com.android.pump.db.DataProvider;
import com.android.pump.db.Episode;
import com.android.pump.db.Movie;
import com.android.pump.db.Series;
import com.android.pump.util.Clog;
import com.android.pump.util.Http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

@WorkerThread
public final class KnowledgeGraph implements DataProvider {
    private static final String TAG = Clog.tag(KnowledgeGraph.class);

    private static final DataProvider INSTANCE = new KnowledgeGraph();

    private KnowledgeGraph() { }

    @AnyThread
    public static @NonNull DataProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean populateArtist(@NonNull Artist artist) throws IOException {
        boolean updated = false;
        // Artist may be of type "Person" or "MusicGroup"
        JSONObject result = getResultFromKG(artist.getName(), "Person", "MusicGroup");

        String imageUrl = getImageUrl(result);
        if (imageUrl != null) {
            updated |= artist.setHeadshotUri(Uri.parse(imageUrl));
        }
        String detailedDescription = getDetailedDescription(result);
        if (detailedDescription != null) {
            updated |= artist.setDescription(detailedDescription);
        }
        return updated;
    }

    @Override
    public boolean populateAlbum(@NonNull Album album) throws IOException {
        // Return if album art is already retrieved from the media file
        if (album.getAlbumArtUri() != null) {
            return false;
        }

        boolean updated = false;
        JSONObject result = getResultFromKG(album.getTitle(), "MusicAlbum");

        // TODO: (b/128383917) Investigate how to filter search results
        String imageUrl = getImageUrl(result);
        if (imageUrl != null) {
            updated |= album.setAlbumArtUri(Uri.parse(imageUrl));
        }
        String detailedDescription = getDetailedDescription(result);
        if (detailedDescription != null) {
            updated |= album.setDescription(detailedDescription);
        }
        return updated;
    }

    @Override
    public boolean populateMovie(@NonNull Movie movie) throws IOException {
        boolean updated = false;
        JSONObject result = getResultFromKG(movie.getTitle(), "Movie");

        String imageUrl = getImageUrl(result);
        if (imageUrl != null) {
            updated |= movie.setPosterUri(Uri.parse(imageUrl));
        }
        String detailedDescription = getDetailedDescription(result);
        if (detailedDescription != null) {
            updated |= movie.setDescription(detailedDescription);
        }
        return updated;
    }

    @Override
    public boolean populateSeries(@NonNull Series series) throws IOException {
        boolean updated = false;
        JSONObject result = getResultFromKG(series.getTitle(), "TVSeries");

        String imageUrl = getImageUrl(result);
        if (imageUrl != null) {
            updated |= series.setPosterUri(Uri.parse(imageUrl));
        }
        String detailedDescription = getDetailedDescription(result);
        if (detailedDescription != null) {
            updated |= series.setDescription(detailedDescription);
        }
        return updated;
    }

    @Override
    public boolean populateEpisode(@NonNull Episode episode) throws IOException {
        boolean updated = false;
        JSONObject result = getResultFromKG(episode.getSeries().getTitle(), "TVEpisode");

        String imageUrl = getImageUrl(result);
        if (imageUrl != null) {
            updated |= episode.setPosterUri(Uri.parse(imageUrl));
        }
        String detailedDescription = getDetailedDescription(result);
        if (detailedDescription != null) {
            updated |= episode.setDescription(detailedDescription);
        }
        return updated;
    }

    private @NonNull JSONObject getResultFromKG(String title, String... types) throws IOException {
        try {
            JSONObject root = (JSONObject) getContent(getContentUri(title, types));
            JSONArray items = root.getJSONArray("itemListElement");
            JSONObject item = (JSONObject) items.get(0);
            JSONObject result = item.getJSONObject("result");
            if (!title.equals(result.getString("name"))) {
                throw new IOException("Failed to find result for " + title);
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Failed to find result for " + title);
        }
    }

    private @Nullable String getImageUrl(@NonNull JSONObject result) {
        String imageUrl = null;
        try {
            JSONObject imageObj = result.optJSONObject("image");
            if (imageObj != null) {
                String url = imageObj.getString("contentUrl");
                if (url != null) {
                    // TODO (b/125143807): Remove once HTTPS scheme urls are retrieved.
                    imageUrl = url.replaceFirst("^http://", "https://");
                }
            }
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse image url", e);
        }
        return imageUrl;
    }

    private @Nullable String getDescription(@NonNull JSONObject result) {
        String description = null;
        try {
            description = result.getString("description");
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse description", e);
        }
        return description;
    }

    private @Nullable String getDetailedDescription(@NonNull JSONObject result) {
        String detailedDescription = null;
        try {
            JSONObject descriptionObj = result.optJSONObject("detailedDescription");
            if (descriptionObj != null) {
                detailedDescription = descriptionObj.getString("articleBody");
            }
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse detailed description", e);
        }
        return detailedDescription;
    }

    private static @NonNull Uri getContentUri(@NonNull String title, @NonNull String... types) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("kgsearch.googleapis.com");
        ub.appendPath("v1");
        ub.appendEncodedPath("entities:search");
        ub.appendQueryParameter("key", ApiKeys.KG_API);
        ub.appendQueryParameter("limit", "1");
        ub.appendQueryParameter("query", title);
        for (String type : types) {
            ub.appendQueryParameter("types", type);
        }
        return ub.build();
    }

    private static @NonNull Object getContent(@NonNull Uri uri) throws IOException, JSONException {
        return new JSONTokener(new String(Http.get(uri.toString()), StandardCharsets.UTF_8))
                .nextValue();
    }
}
