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
import android.graphics.Bitmap;
import android.net.Uri;

import com.android.volley.Request;
import com.bumptech.glide.request.target.Target;

import java.io.File;

/**
 * Interface for network requester service which can perform generic network requests.
 */
public interface Requester {

    /**
     * Adds the request to a Volley RequestQueue.
     */
    <T> void addToRequestQueue(Request<T> request);

    /**
     * Loads an image from Glide's image cache, or if the image has not already downloaded yet,
     * downloads the image from the given URL. Returns a java.io.File for the unprocessed image.
     * <p>
     * This method should only be called from background threads, for example from
     * AsyncTask#doInBackground.
     */
    File loadImageFile(Uri imageUrl);

    /**
     * Loads an image from Glide's image cache, or if the image has not already downloaded yet,
     * downloads the image from the given URL. Returns a java.io.File for the unprocessed image.
     * <p>
     * This method should only be called from background threads, for example from
     * AsyncTask#doInBackground.
     *
     * @param activity Activity in which this request is made. Allows for early cancellation if the
     *                 activity leaves the foreground.
     */
    void loadImageFileWithActivity(Activity activity, Uri imageUrl, Target<File> target);

    /**
     * Loads an image as a bitmap into the target. This method may be called from either the main UI
     * thread or a background thread, and internally the method will determine whether or not to
     * spawn a separate thread for loading the image.
     */
    void loadImageBitmap(Uri imageUrl, Target<Bitmap> target);
}
