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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.sidebar.AppItem;
import com.android.documentsui.sidebar.Item;
import com.android.documentsui.sidebar.RootItem;

/**
 * A bacis data class stored data which apps row chip required.
 * This is abstract class and it will be implemented by {@link AppData} and {@link RootData},
 * both classes are different by the item is {@link AppItem} or {@link RootItem}.
 */
public abstract class AppsRowItemData {

    private final String mTitle;
    protected final ActionHandler mActionHandler;

    public AppsRowItemData(Item item, ActionHandler actionHandler) {
        mTitle = item.title;
        mActionHandler = actionHandler;
    }

    public final String getTitle() {
        return mTitle;
    }

    protected abstract Drawable getIconDrawable(Context context);
    protected abstract void onClicked();
    protected abstract boolean showExitIcon();

    public static class AppData extends AppsRowItemData {

        private final ResolveInfo mResolveInfo;

        public AppData(AppItem item, ActionHandler actionHandler) {
            super(item, actionHandler);
            mResolveInfo = item.info;
        }

        @Override
        protected Drawable getIconDrawable(Context context) {
            return mResolveInfo.loadIcon(context.getPackageManager());
        }

        @Override
        protected void onClicked() {
            mActionHandler.openRoot(mResolveInfo);
        }

        @Override
        protected boolean showExitIcon() {
            return true;
        }
    }

    public static class RootData extends AppsRowItemData {

        private final RootInfo mRootInfo;

        public RootData(RootItem item, ActionHandler actionHandler) {
            super(item, actionHandler);
            mRootInfo = item.root;
        }

        @Override
        protected Drawable getIconDrawable(Context context) {
            return mRootInfo.loadIcon(context);
        }

        @Override
        protected void onClicked() {
            mActionHandler.openRoot(mRootInfo);
        }

        @Override
        protected boolean showExitIcon() {
            return false;
        }
    }
}
