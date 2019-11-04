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
package com.android.wallpaper.model;

import androidx.annotation.Nullable;

/**
 * Fetches and provides wallpaper categories to any registered {@link CategoryReceiver}s.
 */
public interface CategoryProvider {

    /**
     * Fetches the categories asynchronously; once ready, provides results to the given
     * {@link CategoryReceiver}.
     *
     * @param receiver     The receiver of categories.
     * @param forceRefresh Whether to force the CategoryProvider to refresh the categories
     *                     (as opposed to returning cached values from a prior fetch).
     */
    void fetchCategories(CategoryReceiver receiver, boolean forceRefresh);

    int getSize();

    /**
     * Returns the Category at the given index position.
     * <p>
     * Note that this method is expected to be called after the categories have been fetched.
     * @param index index of the Category to return.
     *
     * @throws IllegalStateException if this method is called before fetching happened.
     * @throws IndexOutOfBoundsException if the given index is either negative or larger than
     * {@link #getSize()}
     */
    Category getCategory(int index);

    /**
     * Returns the Category having the given collection ID. If not found, returns null.
     * <p>
     * This method should only be called for collection IDs for which the corresponding Category was
     * already fetched, so the null return case should be treated as an error by callers.
     */
    @Nullable
    Category getCategory(String collectionId);
}
