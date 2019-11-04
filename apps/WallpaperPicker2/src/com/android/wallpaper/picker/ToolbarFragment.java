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
package com.android.wallpaper.picker;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MenuRes;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import androidx.fragment.app.Fragment;

import com.android.wallpaper.R;

/**
 * Base class for Fragments that own a {@link Toolbar} widget.
 * A Fragment extending this class is expected to have a {@link Toolbar} in its root view, with id
 * {@link R.id#toolbar}, which can optionally have a TextView with id custom_toolbar_title for
 * the title.
 * If the Bundle returned by {@link #createArguments(CharSequence)} is used as Arguments for this
 * Fragment, the title provided to that method will be used as the Fragment's toolbar title,
 * otherwise, the value returned by {@link #getDefaultTitle()} (default being {@code null}) will be
 * used as title.
 *
 * @see #setArguments(Bundle)
 */
public abstract class ToolbarFragment extends Fragment implements OnMenuItemClickListener {

    private static final String ARG_TITLE = "ToolbarFragment.title";

    /**
     * Returns a newly created {@link Bundle} containing the given title as an argument.
     * If set as a ToolbarFragment's arguments bundle, this will be used to set up the title of
     * the Toolbar in {@link #setUpToolbar(View)}
     */
    protected static Bundle createArguments(CharSequence title) {
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TITLE, title);
        return args;
    }

    protected Toolbar mToolbar;
    private TextView mTitleView;

    /**
     * Configures a toolbar in the given rootView, with id {@code toolbar} and sets its title to
     * the value in Arguments or {@link #getDefaultTitle()}
     */
    public void setUpToolbar(View rootView) {
        mToolbar = rootView.findViewById(R.id.toolbar);

        mTitleView = mToolbar.findViewById(R.id.custom_toolbar_title);
        CharSequence title;
        if (getArguments() != null) {
            title = getArguments().getCharSequence(ARG_TITLE, getDefaultTitle());
        } else {
            title = getDefaultTitle();
        }
        if (!TextUtils.isEmpty(title)) {
            setTitle(title);
        }
    }

    /**
     * Configures a toolbar in the given rootView, inflating the menu corresponding to the given id
     * for the toolbar menu.
     * Override {@link #onMenuItemClick(MenuItem)} to listen to item click events.
     * @see #setUpToolbar(View)
     */
    public void setUpToolbar(View rootView, @MenuRes int menuResId) {
        setUpToolbar(rootView);
        mToolbar.inflateMenu(menuResId);
        mToolbar.setOnMenuItemClickListener(this);
    }

    /**
     * Provides a title for this Fragment's toolbar to be used if none is found in
     * {@link #getArguments()}.
     * Default implementation returns {@code null}.
     */
    public CharSequence getDefaultTitle() {
        return null;
    }

    private void setTitle(CharSequence title) {
        if (mToolbar == null) {
            return;
        }
        if (mTitleView != null) {
            mToolbar.setTitle(null);
            mTitleView.setText(title);
        } else {
            mToolbar.setTitle(title);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }
}
