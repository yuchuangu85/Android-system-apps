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

package com.android.documentsui.picker;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.Model;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.State;
import com.android.documentsui.queries.SearchViewManager;

import java.util.List;
import java.util.function.IntFunction;
import com.android.documentsui.R;

public final class MenuManager extends com.android.documentsui.MenuManager {

    private boolean mOnlyDirectory;

    public MenuManager(SearchViewManager searchManager, State displayState, DirectoryDetails dirDetails) {
        super(searchManager, displayState, dirDetails);

    }

    @Override
    public void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier) {
        // None as of yet.
    }

    private boolean picking() {
        return mState.action == ACTION_CREATE
                || mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_PICK_COPY_DESTINATION;
    }

    @Override
    public void updateOptionMenu(Menu menu) {
        super.updateOptionMenu(menu);
        if (picking()) {
            // May already be hidden because the root
            // doesn't support search.
            mSearchManager.showMenu(null);
        }
    }

    @Override
    public void updateModel(Model model) {
        for (String id : model.getModelIds()) {
            Cursor cursor = model.getItem(id);
            String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (!MimeTypes.mimeMatches(Document.MIME_TYPE_DIR, docMimeType)) {
                mOnlyDirectory = false;
                return;
            }
        }
        mOnlyDirectory = true;
    }

    @Override
    protected void updateModePicker(MenuItem grid, MenuItem list) {
        // No display options in recent directories
        if (picking() && mDirDetails.isInRecents()) {
            grid.setVisible(false);
            list.setVisible(false);
        } else {
            super.updateModePicker(grid, list);
        }
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        boolean visible = mState.allowMultiple;
        boolean enabled = visible && !mOnlyDirectory;
        selectAll.setVisible(visible);
        selectAll.setEnabled(enabled);
    }

    @Override
    protected void updateCreateDir(MenuItem createDir) {
        createDir.setVisible(picking());
        createDir.setEnabled(picking() && mDirDetails.canCreateDirectory());
    }

    @Override
    protected void updateSelect(MenuItem select, SelectionDetails selectionDetails) {
        select.setVisible(mState.action == ACTION_GET_CONTENT
                || mState.action == ACTION_OPEN);
        select.setEnabled(selectionDetails.size() > 0);
        select.setTitle(R.string.menu_select);
    }
}
