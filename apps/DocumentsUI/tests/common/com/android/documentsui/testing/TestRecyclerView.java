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

package com.android.documentsui.testing;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.InstrumentationRegistry;

import com.android.documentsui.dirlist.TestDocumentsAdapter;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class TestRecyclerView extends RecyclerView {

    private List<RecyclerView.ViewHolder> mHolders = new ArrayList<>();
    private TestDocumentsAdapter adapter;
    private RecyclerView.LayoutManager mLayoutManager;

    public TestRecyclerView(Context context) {
        super(context);
    }

    @Override
    public ViewHolder getChildViewHolder(View child) {
        return mHolders.get(0);
    }

    public void setHolders(List<ViewHolder> holders) {
        mHolders = holders;
    }

    @Override
    public ViewHolder findViewHolderForAdapterPosition(int position) {
        return mHolders.get(position);
    }

    @Override
    public void addOnScrollListener(OnScrollListener listener) {
    }

    @Override
    public void smoothScrollToPosition(int position) {
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return adapter;
    }

    @Override
    public void setLayoutManager(LayoutManager manager) {
        mLayoutManager = manager;
    }

    @Override
    public RecyclerView.LayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    public void setItems(List<String> modelIds) {
        mHolders = new ArrayList<>();
        for (String modelId: modelIds) {
            mHolders.add(new TestViewHolder(Views.createTestView()));
        }
        adapter.updateTestModelIds(modelIds);
    }

    public static TestRecyclerView create(List<String> modelIds) {
        final TestRecyclerView view =
                new TestRecyclerView(InstrumentationRegistry.getTargetContext());
        view.mHolders = new ArrayList<>();
        for (String modelId: modelIds) {
            view.mHolders.add(new TestViewHolder(Views.createTestView()));
        }
        view.adapter = new TestDocumentsAdapter(modelIds);
        return view;
    }

    public void assertItemViewFocused(int pos) {
        Mockito.verify(mHolders.get(pos).itemView).requestFocus();
    }

    private static class TestViewHolder extends RecyclerView.ViewHolder {
        public TestViewHolder(View itemView) {
            super(itemView);
        }
    }
}
