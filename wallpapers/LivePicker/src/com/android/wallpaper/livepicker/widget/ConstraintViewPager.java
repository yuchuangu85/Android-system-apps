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

package com.android.wallpaper.livepicker.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.android.wallpaper.livepicker.R;

/**
 * When ConstraintViewPager is being measured, it will get all height of pages and makes itself
 * height as the same as the maximum height.
 */
public class ConstraintViewPager extends ViewPager {

    private final int mExtraSpacerHeight;

    public ConstraintViewPager(@NonNull Context context) {
        this(context, null /* attrs */);
    }

    public ConstraintViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mExtraSpacerHeight = context.getResources().getDimensionPixelSize(
                R.dimen.preview_attribution_pane_extra_spacer_height);
    }

    /**
     * Visit all child views first and then determine the maximum height for ViewPager.
     * Info page will add extra height in top area determined by empty space.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxChildHeight = 0;
        int infoChildHeight = 0;
        int infoTopPadding = 0;
        View infoPage = null;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            view.measure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0 /* size */, MeasureSpec.UNSPECIFIED));
            int childHeight = view.getMeasuredHeight();
            // Get info page height and top padding.
            if (view.getId() == R.id.page_info) {
                infoPage = view;
                infoChildHeight = childHeight;
                infoTopPadding = view.getPaddingTop();
            }
            if (childHeight > maxChildHeight) {
                maxChildHeight = childHeight;
            }
        }

        // Add extra padding in info page top area if info page has enough empty space to
        // accommodate above and below extra height.
        // 1. "infoChildHeight - infoTopPadding" means info page height without extra padding.
        // 2. "mExtraSpacerHeight * 2" means above and below extra height.
        if (maxChildHeight > (infoChildHeight - infoTopPadding + mExtraSpacerHeight * 2)) {
            if (infoPage != null && infoTopPadding != mExtraSpacerHeight) {
                infoPage.setPadding(infoPage.getPaddingLeft(), mExtraSpacerHeight,
                        infoPage.getPaddingRight(), infoPage.getPaddingBottom());
            }
        }

        if (maxChildHeight != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxChildHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
