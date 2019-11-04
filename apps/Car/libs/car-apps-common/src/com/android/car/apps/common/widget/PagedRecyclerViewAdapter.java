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

package com.android.car.apps.common.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.R;

/**
 * The adapter for the parent recyclerview in {@link PagedRecyclerView} widget.
 */
final class PagedRecyclerViewAdapter
        extends RecyclerView.Adapter<PagedRecyclerViewAdapter.NestedRowViewHolder> {

    @Override
    public PagedRecyclerViewAdapter.NestedRowViewHolder onCreateViewHolder(ViewGroup parent,
            int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.paged_recycler_view_item, parent, false);
        return new NestedRowViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager). Intentionally left empty
    // since this adapter is an empty shell for the nested recyclerview.
    @Override
    public void onBindViewHolder(NestedRowViewHolder holder, int position) {
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return 1;
    }

    /**
     * The viewholder class for the parent recyclerview.
     */
    static class NestedRowViewHolder extends RecyclerView.ViewHolder {
        public FrameLayout mFrameLayout;

        NestedRowViewHolder(View view) {
            super(view);
            mFrameLayout = view.findViewById(R.id.nested_recycler_view_layout);
        }
    }
}
