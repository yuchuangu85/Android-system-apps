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

package com.android.documentsui.dirlist;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.R;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AppsRowItemData.AppData;
import com.android.documentsui.dirlist.AppsRowItemData.RootData;
import com.android.documentsui.sidebar.AppItem;
import com.android.documentsui.sidebar.Item;
import com.android.documentsui.sidebar.RootItem;

import java.util.ArrayList;
import java.util.List;

/**
 * A manager class stored apps row chip data list. Data will be synced by RootsFragment.
 */
public class AppsRowManager {

    private final ActionHandler mActionHandler;
    private final List<AppsRowItemData> mDataList;

    public AppsRowManager(ActionHandler handler) {
        mDataList = new ArrayList<>();
        mActionHandler = handler;
    }

    public List<AppsRowItemData> updateList(List<Item> itemList) {
        mDataList.clear();
        for (Item item : itemList) {
            if (item instanceof RootItem) {
                mDataList.add(new RootData((RootItem) item, mActionHandler));
            } else {
                mDataList.add(new AppData((AppItem) item, mActionHandler));
            }
        }
        return mDataList;
    }

    private boolean shouldShow(State state) {
        boolean isHiddenAction = state.action == State.ACTION_CREATE
                || state.action == State.ACTION_OPEN_TREE
                || state.action == State.ACTION_PICK_COPY_DESTINATION;
        return state.stack.isRecents() && !isHiddenAction && mDataList.size() > 0;
    }

    public void updateView(BaseActivity activity) {
        final View appsRowLayout = activity.findViewById(R.id.apps_row);

        if (!shouldShow(activity.getDisplayState())) {
            appsRowLayout.setVisibility(View.GONE);
            return;
        }

        appsRowLayout.setVisibility(View.VISIBLE);
        final LinearLayout appsGroup = activity.findViewById(R.id.apps_group);
        appsGroup.removeAllViews();

        final LayoutInflater inflater = activity.getLayoutInflater();
        for (AppsRowItemData data : mDataList) {
            View item = inflater.inflate(R.layout.apps_item, appsGroup, false);
            bindView(item, data);
            appsGroup.addView(item);
        }
    }

    private void bindView(View view, AppsRowItemData data) {
        final ImageView app_icon = view.findViewById(R.id.app_icon);
        final TextView title = view.findViewById(android.R.id.title);
        final ImageView exit_icon = view.findViewById(R.id.exit_icon);

        app_icon.setImageDrawable(data.getIconDrawable(view.getContext()));
        title.setText(data.getTitle());
        exit_icon.setVisibility(data.showExitIcon() ? View.VISIBLE : View.GONE);
        view.setOnClickListener(v -> data.onClicked());
    }
}
