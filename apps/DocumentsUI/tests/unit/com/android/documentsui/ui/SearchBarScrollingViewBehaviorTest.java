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

package com.android.documentsui.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.test.InstrumentationRegistry;

import com.android.documentsui.R;

import com.google.android.material.appbar.AppBarLayout;

import org.junit.Before;
import org.junit.Test;


public class SearchBarScrollingViewBehaviorTest {
    private SearchBarScrollingViewBehavior mScrollingViewBehavior;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mScrollingViewBehavior = new SearchBarScrollingViewBehavior(mContext, null);
    }

    @Test
    public void shouldHeaderOverlapScrollingChild_returnTrue() {
        assertTrue(mScrollingViewBehavior.shouldHeaderOverlapScrollingChild());
    }

    @Test
    public void setAppBarLayoutTransparent_defaultWhiteBackground_shouldBeTransparent() {
        mContext.setTheme(R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);
        final CoordinatorLayout coordinatorLayout = new CoordinatorLayout(mContext);
        final AppBarLayout appBarLayout = new AppBarLayout(mContext);
        final CoordinatorLayout.LayoutParams lp = mock(CoordinatorLayout.LayoutParams.class);
        lp.setBehavior(mScrollingViewBehavior);
        appBarLayout.setLayoutParams(lp);
        appBarLayout.setBackgroundColor(Color.WHITE);
        mScrollingViewBehavior.onDependentViewChanged(coordinatorLayout, null, appBarLayout);

        assertEquals(Color.TRANSPARENT, ((ColorDrawable) appBarLayout.getBackground()).getColor());
    }
}
