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

package com.android.documentsui.sidebar;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.provider.DocumentsProvider;
import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;

/**
 * An {@link Item} for each root provided by {@link DocumentsProvider}s
 * and apps that supports some picking actions like {@link Intent#ACTION_GET_CONTENT}.
 * This is only used in pickers.
 */
class RootAndAppItem extends RootItem {

    public final ResolveInfo resolveInfo;

    public RootAndAppItem(RootInfo root, ResolveInfo info, ActionHandler actionHandler) {
        super(root, actionHandler, info.activityInfo.packageName);
        this.resolveInfo = info;
    }

    @Override
    boolean showAppDetails() {
        mActionHandler.showAppDetails(resolveInfo);
        return true;
    }

    @Override
    public void bindView(View convertView) {
        final Context context = convertView.getContext();

        String contentDescription =
                context.getResources().getString(R.string.open_external_app, root.title);

        bindAction(convertView, View.VISIBLE, R.drawable.ic_exit_to_app, contentDescription);
        bindIconAndTitle(convertView);
        bindSummary(convertView, root.summary);
    }

    @Override
    protected void onActionClick(View view) {
        mActionHandler.openRoot(resolveInfo);
    }

    @Override
    public String toString() {
        return "RootAndAppItem{"
                + "id=" + stringId
                + ", root=" + root
                + ", resolveInfo=" + resolveInfo
                + ", docInfo=" + docInfo
                + "}";
    }
}
