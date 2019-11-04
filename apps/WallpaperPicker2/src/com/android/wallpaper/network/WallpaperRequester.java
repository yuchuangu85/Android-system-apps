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
package com.android.wallpaper.network;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;

/**
 * Default implementation of {@link Requester}.
 */
public class WallpaperRequester implements Requester {
    // Network timeout that matches expected outer bound of packet loss duration on slower networks,
    // i.e., 2g.
    public static final int LONG_TIMEOUT_MS = 10000;

    private static final String TAG = "WallpaperRequester";

    private RequestQueue mRequestQueue;
    private Context mAppContext;

    public WallpaperRequester(Context context) {
        mAppContext = context.getApplicationContext();
        mRequestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    @Override
    public <T> void addToRequestQueue(Request<T> request) {
        mRequestQueue.add(request);
    }

    @Override
    public File loadImageFile(Uri imageUrl) {
        try {
            return Glide.with(mAppContext)
                    .downloadOnly()
                    .load(imageUrl)
                    // Apply a longer timeout duration to avoid crashing on networks with long packet loss
                    // durations.
                    .apply(RequestOptions.option(HttpGlideUrlLoader.TIMEOUT, LONG_TIMEOUT_MS))
                    .submit()
                    .get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to get File for image with url: " + imageUrl);
            return null;
        }
    }

    @Override
    public void loadImageFileWithActivity(Activity activity, Uri imageUrl, Target<File> target) {
        Glide.with(activity)
                .asFile()
                .load(imageUrl)
                // Apply a longer timeout duration to avoid crashing on networks with long packet loss
                // durations.
                .apply(RequestOptions.option(HttpGlideUrlLoader.TIMEOUT, LONG_TIMEOUT_MS))
                .into(target);
    }

    @Override
    public void loadImageBitmap(Uri imageUrl, Target<Bitmap> target) {
        try {
            Glide.with(mAppContext)
                    .asBitmap()
                    .load(imageUrl)
                    .apply(RequestOptions.noTransformation())
                    .apply(RequestOptions.option(HttpGlideUrlLoader.TIMEOUT, LONG_TIMEOUT_MS))
                    .into(target);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get Bitmap for image with url: " + imageUrl, e);
        }
    }
}
