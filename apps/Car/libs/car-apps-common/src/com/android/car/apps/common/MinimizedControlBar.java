/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.apps.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * This is a compact CarControlBar that provides a fixed number of controls (with no overflow),
 * along with some metadata (title, subtitle, icon)
 */
public class MinimizedControlBar extends ConstraintLayout implements CarControlBar {

    // All slots in this action bar where 0 is the bottom-start corner of the matrix, and
    // mNumColumns * nNumRows - 1 is the top-end corner
    private FrameLayout[] mSlots;

    /** Views to set in a particular {@link ControlBar.SlotPosition} */
    private final View[] mFixedViews = new View[3];
    // Holds the views to show as buttons
    private View[] mViews;

    protected TextView mTitle;
    protected TextView mSubtitle;
    protected ImageView mContentTile;

    private static final int NUM_COLUMNS = 3;

    public MinimizedControlBar(Context context) {
        this(context, null, 0);
    }

    public MinimizedControlBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public MinimizedControlBar(Context context, AttributeSet attrs, int defStyleAttrs) {
        this(context, attrs, defStyleAttrs, R.layout.minimized_control_bar);
    }

    protected MinimizedControlBar(Context context, AttributeSet attrs, int defStyleAttrs,
            int layoutId) {
        super(context, attrs, defStyleAttrs);
        init(context, layoutId);
    }

    private void init(Context context, int layoutId) {
        inflate(context, layoutId, this);
        mViews = new View[NUM_COLUMNS];
        mTitle = findViewById(R.id.minimized_control_bar_title);
        mSubtitle = findViewById(R.id.minimized_control_bar_subtitle);
        mContentTile = findViewById(R.id.minimized_control_bar_content_tile);

        mSlots = new FrameLayout[NUM_COLUMNS];

        mSlots[0] = findViewById(R.id.minimized_control_bar_left_slot);
        mSlots[1] = findViewById(R.id.minimized_control_bar_main_slot);
        mSlots[2] = findViewById(R.id.minimized_control_bar_right_slot);
    }

    @Override
    public void setView(@Nullable View view, @SlotPosition int slotPosition) {
        mFixedViews[slotPosition] = view;
        updateViewsLayout();
    }

    @Override
    public void setViews(@Nullable View[] views) {
        mViews = views;
        updateViewsLayout();
    }

    @Override
    public ImageButton createIconButton(Drawable icon) {
        return createIconButton(icon, R.layout.control_bar_button);
    }

    @Override
    public ImageButton createIconButton(Drawable icon, int viewId) {
        LayoutInflater inflater = LayoutInflater.from(mSlots[0].getContext());
        final boolean attachToRoot = false;
        ImageButton button = (ImageButton) inflater.inflate(viewId, mSlots[0], attachToRoot);
        button.setImageDrawable(icon);
        return button;
    }

    private void updateViewsLayout() {
        int viewIndex = 0;
        // Fill in slots with provided views
        for (int i = 0; i < NUM_COLUMNS; i++) {
            View viewToUse = null;
            if (mFixedViews[i] != null) {
                viewToUse = mFixedViews[i];
            } else if (mViews != null && viewIndex < mViews.length) {
                // If views are not provided explicitly for a slot, use the next value in the list
                viewToUse = mViews[viewIndex];
                viewIndex++;
            }
            setView(viewToUse, mSlots[CarControlBar.getSlotIndex(i, NUM_COLUMNS)]);
        }
    }

    private void setView(@Nullable View view, FrameLayout container) {
        container.removeAllViews();
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            // As we are removing views (on BT disconnect, for example), some items will be
            // shifting from expanded to collapsed - remove those from the group before adding to
            // the new slot
            if (view.getParent() != null) {
                parent.removeView(view);
            }
            container.addView(view);
            container.setVisibility(VISIBLE);
        } else {
            container.setVisibility(INVISIBLE);
        }
    }

}
