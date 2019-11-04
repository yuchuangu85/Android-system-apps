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
package com.android.wallpaper.widget;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;

/**
 * Decorates a grid view item with margins on each side. Note that this pads on the bottom and
 * right, so the containing RecyclerView should add {@code paddingTop} and {@code paddingLeft} to
 * make things look even.
 */
public class GridMarginDecoration extends ItemDecoration {
    private static final String TAG = "GridMarginDecoration";
    private int horizontalMargin;
    private int verticalMargin;

    public GridMarginDecoration(int horizontalMargin, int verticalMargin) {
        this.horizontalMargin = horizontalMargin;
        this.verticalMargin = verticalMargin;
    }

    /**
     * Applies a GridMarginDecoration to the specified recyclerView, calculating the horizontal and
     * vertical margin based on the view's {@code paddingLeft} and {@code paddingTop}.
     */
    public static void applyTo(RecyclerView recyclerView) {
        int horizontal = recyclerView.getPaddingLeft();
        int vertical = recyclerView.getPaddingTop();
        if (recyclerView.getPaddingRight() != 0 || recyclerView.getPaddingBottom() != 0) {
            Log.d(TAG, "WARNING: the view being decorated has right and/or bottom padding, which will "
                    + "make the post-decoration grid unevenly padded");
        }

        recyclerView.addItemDecoration(new GridMarginDecoration(horizontal, vertical));
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                               RecyclerView.State state) {
        outRect.set(0, 0, horizontalMargin, verticalMargin);
    }
}
