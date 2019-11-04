/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.apps.common.util;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.widget.PagedRecyclerView;
import com.android.car.apps.common.widget.PagedRecyclerView.ScrollBarPosition;

/**
 * An abstract class that defines required contract for a custom scroll bar for the
 * {@link PagedRecyclerView}. All custom scroll bar must inherit from this class.
 */
public abstract class ScrollBarUI {
    protected RecyclerView mRecyclerView;

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * The concrete class should implement this method to initialize configuration of a scrollbar
     * view.
     */
    public abstract void initialize(Context context, RecyclerView recyclerView,
            int scrollBarContainerWidth, @ScrollBarPosition int scrollBarPosition,
            boolean scrollBarAboveRecyclerView);

    /**
     * Requests layout of the scrollbar. Should be called when there's been a change that will
     * affect the size of the scrollbar view.
     */
    public abstract void requestLayout();

    /**
     * Sets the padding of the scrollbar, relative to the padding of the RecyclerView.
     */
    public abstract void setPadding(int padddingStart, int paddingEnd);
}
