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

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Outline provider that allows to only have a rounded top or a rounded bottom. Used for list items
 * so entire list looks like a card.
 */
public class ListItemOutlineProvider extends ViewOutlineProvider {

    private boolean mHasRoundedTop;
    private boolean mHasRoundedBottom;
    private final float mRadius;

    public ListItemOutlineProvider(float radius) {
        mRadius = radius;
    }

    public void setCorners(boolean hasRoundedTop, boolean hasRoundedBottom) {
        mHasRoundedTop = hasRoundedTop;
        mHasRoundedBottom = hasRoundedBottom;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        int left = 0;
        int right = view.getWidth();
        int top = 0;
        int bottom = view.getHeight();

        // If we don't want top rounded corner, the top outline should clip the top above the view
        // from -radius to 0.
        if (!mHasRoundedTop) {
            top -= mRadius;
        }
        // If we don't want bottom round corner, the bottom outline should clip the bottom below the
        // view starting from view's height to view's height + radius.
        if (!mHasRoundedBottom) {
            bottom += mRadius;
        }
        outline.setRoundRect(left, top, right, bottom, mRadius);
    }
}
