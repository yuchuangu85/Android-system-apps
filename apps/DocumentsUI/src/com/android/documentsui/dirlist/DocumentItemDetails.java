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

import android.view.MotionEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

/**
 * Provide information of a specific RecyclerView item for selection.
 */
public final class DocumentItemDetails extends ItemDetails<String> {
    private final DocumentHolder mDocumentHolder;

    DocumentItemDetails(DocumentHolder holder) {
        mDocumentHolder = holder;
    }

    /**
     * @return The view type of this ViewHolder.
     */
    public int getItemViewType() {
        return mDocumentHolder.getItemViewType();
    }

    @Override
    public int getPosition() {
        return mDocumentHolder.getAdapterPosition();
    }

    @Override
    public String getSelectionKey() {
        return mDocumentHolder.getModelId();
    }

    @Override
    public boolean inDragRegion(MotionEvent e) {
        return mDocumentHolder.inDragRegion(e);
    }

    @Override
    public boolean inSelectionHotspot(MotionEvent e) {
        return mDocumentHolder.inSelectRegion(e);
    }

    public boolean inPreviewIconHotspot(MotionEvent e) {
        return mDocumentHolder.inPreviewIconRegion(e);
    }
}
