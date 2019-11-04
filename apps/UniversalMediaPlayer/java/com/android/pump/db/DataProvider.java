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

import androidx.annotation.NonNull;

import java.io.IOException;

// TODO (b/126977959): Split DataProvider into Audio/VideoDataProvider interfaces.
public interface DataProvider {
    boolean populateArtist(@NonNull Artist artist) throws IOException;
    boolean populateAlbum(@NonNull Album album) throws IOException;
    boolean populateMovie(@NonNull Movie movie) throws IOException;
    boolean populateSeries(@NonNull Series series) throws IOException;
    boolean populateEpisode(@NonNull Episode episode) throws IOException;
}
