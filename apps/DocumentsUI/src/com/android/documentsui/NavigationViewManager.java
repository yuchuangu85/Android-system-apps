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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.VERBOSE;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.function.IntConsumer;

/**
 * A facade over the portions of the app and drawer toolbars.
 */
public class NavigationViewManager {

    private static final String TAG = "NavigationViewManager";

    private final DrawerController mDrawer;
    private final Toolbar mToolbar;
    private final State mState;
    private final NavigationViewManager.Environment mEnv;
    private final Breadcrumb mBreadcrumb;
    private final View mSearchBarView;
    private final CollapsingToolbarLayout mCollapsingBarLayout;
    private final Drawable mDefaultActionBarBackground;
    private final ViewOutlineProvider mSearchBarOutlineProvider;
    private final boolean mShowSearchBar;

    public NavigationViewManager(
            Activity activity,
            DrawerController drawer,
            State state,
            NavigationViewManager.Environment env,
            Breadcrumb breadcrumb) {

        mToolbar = activity.findViewById(R.id.toolbar);
        mDrawer = drawer;
        mState = state;
        mEnv = env;
        mBreadcrumb = breadcrumb;
        mBreadcrumb.setup(env, state, this::onNavigationItemSelected);

        mToolbar.setNavigationOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onNavigationIconClicked();
                    }
                });
        mSearchBarView = activity.findViewById(R.id.searchbar_title);
        mCollapsingBarLayout = activity.findViewById(R.id.collapsing_toolbar);
        mDefaultActionBarBackground = mToolbar.getBackground();
        mShowSearchBar = activity.getResources().getBoolean(R.bool.show_search_bar);

        final Resources resources = mToolbar.getResources();
        final int radius = resources.getDimensionPixelSize(R.dimen.search_bar_radius);
        final int marginStart =
                resources.getDimensionPixelSize(R.dimen.search_bar_background_margin_start);
        final int marginEnd =
                resources.getDimensionPixelSize(R.dimen.search_bar_background_margin_end);
        mSearchBarOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(marginStart, 0,
                        view.getWidth() - marginEnd, view.getHeight(), radius);
            }
        };
    }

    public void setSearchBarClickListener(View.OnClickListener listener) {
        mSearchBarView.setOnClickListener(listener);
    }

    private void onNavigationIconClicked() {
        if (mDrawer.isPresent()) {
            mDrawer.setOpen(true);
        }
    }

    void onNavigationItemSelected(int position) {
        boolean changed = false;
        while (mState.stack.size() > position + 1) {
            changed = true;
            mState.stack.pop();
        }
        if (changed) {
            mEnv.refreshCurrentRootAndDirectory(AnimationView.ANIM_LEAVE);
        }
    }

    public void update() {
        updateScrollFlag();
        updateToolbar();

        // TODO: Looks to me like this block is never getting hit.
        if (mEnv.isSearchExpanded()) {
            mToolbar.setTitle(null);
            mBreadcrumb.show(false);
            return;
        }

        mDrawer.setTitle(mEnv.getDrawerTitle());

        mToolbar.setNavigationIcon(getActionBarIcon());
        mToolbar.setNavigationContentDescription(R.string.drawer_open);

        if (shouldShowSearchBar()) {
            mBreadcrumb.show(false);
            mToolbar.setTitle(null);
            mSearchBarView.setVisibility(View.VISIBLE);
        } else if (mState.stack.size() <= 1) {
            mBreadcrumb.show(false);
            mSearchBarView.setVisibility(View.GONE);
            String title = mEnv.getCurrentRoot().title;
            if (VERBOSE) Log.v(TAG, "New toolbar title is: " + title);
            mToolbar.setTitle(title);
        } else {
            mBreadcrumb.show(true);
            mToolbar.setTitle(null);
            mSearchBarView.setVisibility(View.GONE);
            mBreadcrumb.postUpdate();
        }
    }

    private void updateScrollFlag() {
        if (mCollapsingBarLayout == null) {
            return;
        }

        AppBarLayout.LayoutParams lp =
                (AppBarLayout.LayoutParams) mCollapsingBarLayout.getLayoutParams();
        if (shouldShowSearchBar()) {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                            | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                            | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED);
        } else {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                            | AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
        }
        mCollapsingBarLayout.setLayoutParams(lp);
    }

    private void updateToolbar() {
        if (shouldShowSearchBar()) {
            mToolbar.setBackgroundResource(R.drawable.search_bar_background);
            mToolbar.setOutlineProvider(mSearchBarOutlineProvider);
        } else {
            mToolbar.setBackground(mDefaultActionBarBackground);
            mToolbar.setOutlineProvider(null);
        }

        if (mCollapsingBarLayout != null) {
            View overlayBackground =
                    mCollapsingBarLayout.findViewById(R.id.toolbar_background_layout);
            overlayBackground.setVisibility(shouldShowSearchBar() ? View.GONE : View.VISIBLE);
        }
    }

    private boolean shouldShowSearchBar() {
        return mState.stack.isRecents() && !mEnv.isSearchExpanded() && mShowSearchBar;
    }

    // Hamburger if drawer is present, else sad nullness.
    private @Nullable Drawable getActionBarIcon() {
        if (mDrawer.isPresent()) {
            return mToolbar.getContext().getDrawable(R.drawable.ic_hamburger);
        } else {
            return null;
        }
    }

    void revealRootsDrawer(boolean open) {
        mDrawer.setOpen(open);
    }

    interface Breadcrumb {
        void setup(Environment env, State state, IntConsumer listener);
        void show(boolean visibility);
        void postUpdate();
    }

    interface Environment {
        @Deprecated  // Use CommonAddones#getCurrentRoot
        RootInfo getCurrentRoot();
        String getDrawerTitle();
        @Deprecated  // Use CommonAddones#refreshCurrentRootAndDirectory
        void refreshCurrentRootAndDirectory(int animation);
        boolean isSearchExpanded();
    }
}
