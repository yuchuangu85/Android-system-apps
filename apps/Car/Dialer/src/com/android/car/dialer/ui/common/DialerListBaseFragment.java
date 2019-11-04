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

package com.android.car.dialer.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.widget.PagedRecyclerView;
import com.android.car.dialer.R;

/**
 * Base fragment that inflates a {@link RecyclerView}. It handles the top offset for first row item
 * so the list can scroll underneath the top bar.
 */
public class DialerListBaseFragment extends DialerBaseFragment {

    private PagedRecyclerView mListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResource(), container, false);
        mListView = view.findViewById(R.id.list_view);
        mListView.setLayoutManager(createLayoutManager());
        mListView.setPaddingRelative(mListView.getPaddingStart(), getTopOffset(),
                mListView.getPaddingEnd(), mListView.getPaddingBottom());
        return view;
    }

    /** Layout resource for this fragment. It must contains a RecyclerView with id list_view. */
    @LayoutRes
    protected int getLayoutResource() {
        return R.layout.list_fragment;
    }

    /**
     * Creates the layout manager for the recycler view. Default is a {@link LinearLayoutManager}.
     * Child inheriting from this fragment can override to create a different layout manager.
     */
    @NonNull
    protected RecyclerView.LayoutManager createLayoutManager() {
        return new LinearLayoutManager(getContext());
    }

    /** Returns the {@link RecyclerView} instance. */
    @NonNull
    protected PagedRecyclerView getRecyclerView() {
        return mListView;
    }

    /** Gets the top padding for the list. By default it includes the action bar's height */
    protected int getTopOffset() {
        int listTopPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.list_top_padding);
        return getTopBarHeight() + listTopPadding;
    }
}
