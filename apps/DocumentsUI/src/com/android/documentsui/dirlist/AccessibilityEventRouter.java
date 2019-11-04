/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate;

import java.util.function.Function;

/**
 * Custom Accessibility Delegate for RecyclerViews to route click events on its child views to
 * proper handlers, and to surface selection state to a11y events.
 * <p>
 * The majority of event handling isdone using TouchDetector instead of View.OnCLickListener, which
 * most a11y services use to understand whether a particular view is clickable or not. Thus, we need
 * to use a custom accessibility delegate to manually add ACTION_CLICK to clickable child views'
 * accessibility node, and then correctly route these clicks done by a11y services to responsible
 * click callbacks.
 * <p>
 * DocumentsUI uses {@link View#setActivated(boolean)} instead of {@link View#setSelected(boolean)}
 * for marking a view as selected. We will surface that selection state to a11y services in this
 * class.
 */
public class AccessibilityEventRouter extends RecyclerViewAccessibilityDelegate {

    private final ItemDelegate mItemDelegate;
    private final Function<View, Boolean> mClickCallback;
    private final Function<View, Boolean> mLongClickCallback;

    public AccessibilityEventRouter(
            RecyclerView recyclerView, @NonNull Function<View, Boolean> clickCallback,
            @Nullable Function<View, Boolean> longClickCallback) {
        super(recyclerView);
        mClickCallback = clickCallback;
        mLongClickCallback = longClickCallback;
        mItemDelegate = new ItemDelegate(this) {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                final RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(host);
                // if the viewHolder is a DocumentsHolder instance and the ItemDetails
                // is null, it can't be clicked
                if (holder instanceof DocumentHolder) {
                    if (((DocumentHolder)holder).getItemDetails() != null) {
                        addAction(info);
                    }
                } else {
                    addAction(info);
                }
                info.setSelected(host.isActivated());
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                // We are only handling click events; route all other to default implementation
                if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                    return mClickCallback.apply(host);
                } else if (action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK
                        && mLongClickCallback != null) {
                    return mLongClickCallback.apply(host);
                }
                return super.performAccessibilityAction(host, action, args);
            }
        };
    }

    @Override
    public AccessibilityDelegateCompat getItemDelegate() {
        return mItemDelegate;
    }

    private void addAction(AccessibilityNodeInfoCompat info) {
        info.addAction(AccessibilityActionCompat.ACTION_CLICK);
        if (mLongClickCallback != null) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
        }
    }
}