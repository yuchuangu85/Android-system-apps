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

package com.android.pump.ui;

import android.util.SparseBooleanArray;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.pump.R;

@UiThread
public class CustomRecycledViewPool extends RecycledViewPool {
    private static final int MAX_VIEWS = 17;

    private final SparseBooleanArray mInitialized = new SparseBooleanArray();

    public CustomRecycledViewPool() {
        setMaxRecycledViews(R.layout.header, 1);
        setMaxRecycledViews(R.layout.movie, MAX_VIEWS);
    }

    @Override
    public void setMaxRecycledViews(int viewType, int maxViews) {
        super.setMaxRecycledViews(viewType, maxViews);
        mInitialized.put(viewType, true);
    }

    @Override
    public void putRecycledView(@NonNull ViewHolder scrap) {
        int viewType = scrap.getItemViewType();
        if (!mInitialized.get(viewType)) {
            setMaxRecycledViews(viewType, MAX_VIEWS);
            throw new IllegalArgumentException("Unknown view type"); // TODO tuning only -- remove this
        }
        super.putRecycledView(scrap);
    }
}
