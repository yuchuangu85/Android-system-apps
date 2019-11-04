/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.dialer.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.util.Themes;
import com.android.car.dialer.log.L;

/**
 * Branched from {@link androidx.recyclerview.widget.DividerItemDecoration} but can hide the last
 * divider.
 */
public class VerticalListDividerDecoration extends RecyclerView.ItemDecoration {

    private static final String TAG = "CD.VerticalListDividerDecoration";
    private static final int SCROLLING_DOWN = 1;
    private static final int LIST_DIVIDER_ATTR = android.R.attr.listDivider;
    private final boolean mHideLastDivider;
    private final Rect mBounds = new Rect();

    private Drawable mDivider;

    /**
     * Creates a divider {@link RecyclerView.ItemDecoration} that can be used with a vertical
     * {@link LinearLayoutManager}.
     *
     * @param context Current context, it will be used to access resources.
     */
    public VerticalListDividerDecoration(Context context, boolean hideLastDivider) {
        mDivider = Themes.getAttrDrawable(context, LIST_DIVIDER_ATTR);
        if (mDivider == null) {
            L.w(TAG, "@android:attr/listDivider was not set."
                    + " Set divider drawable by calling setDrawable().");
        }

        mHideLastDivider = hideLastDivider;
    }

    /**
     * Sets the {@link Drawable} for this divider.
     *
     * @param drawable Drawable that should be used as a divider.
     */
    public void setDrawable(@NonNull Drawable drawable) {
        if (drawable == null) {
            throw new IllegalArgumentException("Drawable cannot be null.");
        }
        mDivider = drawable;
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (parent.getLayoutManager() == null || mDivider == null) {
            return;
        }
        drawVertical(c, parent);
    }

    private void drawVertical(Canvas canvas, RecyclerView parent) {
        canvas.save();
        final int left;
        final int right;
        if (parent.getClipToPadding()) {
            left = parent.getPaddingLeft();
            right = parent.getWidth() - parent.getPaddingRight();
            canvas.clipRect(left, parent.getPaddingTop(), right,
                    parent.getHeight() - parent.getPaddingBottom());
        } else {
            left = 0;
            right = parent.getWidth();
        }

        final int childCount = parent.getChildCount();
        final int dividerCount = !parent.canScrollVertically(SCROLLING_DOWN) && mHideLastDivider ?
                childCount - 1 : childCount;
        for (int i = 0; i < dividerCount; i++) {
            final View child = parent.getChildAt(i);
            parent.getDecoratedBoundsWithMargins(child, mBounds);
            final int bottom = mBounds.bottom + Math.round(child.getTranslationY());
            final int top = bottom - mDivider.getIntrinsicHeight();
            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(canvas);
        }
        canvas.restore();
    }
}
