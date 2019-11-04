/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.ColorRes;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.documentsui.R;

/**
 * A {@link SwipeRefreshLayout} that does not intercept any touch events. This relies on its nested
 * view to scroll in order to cause a refresh. It is possible that it gets disabled by
 * {@link ListeningGestureDetector} .
 */
public class DocumentsSwipeRefreshLayout extends SwipeRefreshLayout {
    private static final String TAG = DocumentsSwipeRefreshLayout.class.getSimpleName();

    public DocumentsSwipeRefreshLayout(Context context) {
        this(context, null);
    }

    public DocumentsSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        final int[] styledAttrs = {android.R.attr.colorPrimary};

        TypedArray a = context.obtainStyledAttributes(styledAttrs);
        @ColorRes int colorId = a.getResourceId(0, -1);
        if (colorId == -1) {
            Log.w(TAG, "Retrive colorPrimary colorId from theme fail, assign R.color.primary");
            colorId = R.color.primary;
        }
        a.recycle();
        setColorSchemeResources(colorId);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return false;
    }
}