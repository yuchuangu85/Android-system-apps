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

package com.android.documentsui.sidebar;

import androidx.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.IconUtils;
import com.android.documentsui.MenuManager;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

/**
 * An {@link Item} for each root provided by {@link DocumentsProvider}s.
 */
public class RootItem extends Item {
    private static final String STRING_ID_FORMAT = "RootItem{%s/%s}";

    public final RootInfo root;
    public @Nullable DocumentInfo docInfo;

    protected final ActionHandler mActionHandler;
    private final String mPackageName;

    public RootItem(RootInfo root, ActionHandler actionHandler) {
        this(root, actionHandler, "" /* packageName */);
    }

    public RootItem(RootInfo root, ActionHandler actionHandler, String packageName) {
        super(R.layout.item_root, root.title, getStringId(root));
        this.root = root;
        mActionHandler = actionHandler;
        mPackageName = packageName;
    }

    private static String getStringId(RootInfo root) {
        // Empty URI authority is invalid, so we can use empty string if root.authority is null.
        // Directly passing null to String.format() will write "null" which can be a valid URI
        // authority.
        String authority = (root.authority == null ? "" : root.authority);
        return String.format(STRING_ID_FORMAT, authority, root.rootId);
    }

    @Override
    public void bindView(View convertView) {
        final Context context = convertView.getContext();
        if (root.supportsEject()) {
            bindAction(convertView, View.VISIBLE, R.drawable.ic_eject,
                    context.getResources().getString(R.string.menu_eject_root));
        } else {
            bindAction(convertView, View.GONE, -1 /* iconResource */, null /* description */);
        }
        // Show available space if no summary
        String summaryText = root.summary;
        if (TextUtils.isEmpty(summaryText) && root.availableBytes >= 0) {
            summaryText = context.getString(R.string.root_available_bytes,
                    Formatter.formatFileSize(context, root.availableBytes));
        }

        bindIconAndTitle(convertView);
        bindSummary(convertView, summaryText);
    }

    protected final void bindAction(View view, int visibility, int iconId, String description) {
        final ImageView actionIcon = (ImageView) view.findViewById(R.id.action_icon);
        final View verticalDivider = view.findViewById(R.id.vertical_divider);
        final View actionIconArea = view.findViewById(R.id.action_icon_area);

        verticalDivider.setVisibility(visibility);
        actionIconArea.setVisibility(visibility);
        actionIconArea.setOnClickListener(visibility == View.VISIBLE ? this::onActionClick : null);
        if (description != null) {
            actionIconArea.setContentDescription(description);
        }
        if (iconId > 0) {
            actionIcon.setImageDrawable(IconUtils.applyTintColor(view.getContext(), iconId,
                    R.color.item_action_icon));
        }
    }

    protected void onActionClick(View view) {
        RootsFragment.ejectClicked(view, root, mActionHandler);
    }

    protected final void bindIconAndTitle(View view) {
        bindIcon(view, root.loadDrawerIcon(view.getContext()));
        bindTitle(view);
    }

    protected void bindSummary(View view, String summary) {
        final TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        summaryView.setText(summary);
        summaryView.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
    }

    private void bindIcon(View view, Drawable drawable) {
        final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
        icon.setImageDrawable(drawable);
    }

    private void bindTitle(View view) {
        final TextView titleView = (TextView) view.findViewById(android.R.id.title);
        titleView.setText(title);
    }

    @Override
    boolean isRoot() {
        return true;
    }

    @Override
    void open() {
        mActionHandler.openRoot(root);
    }

    @Override
    String getPackageName() {
        return mPackageName;
    }

    @Override
    boolean isDropTarget() {
        return root.supportsCreate();
    }

    @Override
    boolean dropOn(DragEvent event) {
        return mActionHandler.dropOn(event, root);
    }

    @Override
    void createContextMenu(Menu menu, MenuInflater inflater, MenuManager menuManager) {
        inflater.inflate(R.menu.root_context_menu, menu);
        menuManager.updateRootContextMenu(menu, root, docInfo);
    }

    @Override
    public String toString() {
        return "RootItem{"
                + "id=" + stringId
                + ", root=" + root
                + ", docInfo=" + docInfo
                + "}";
    }
}
