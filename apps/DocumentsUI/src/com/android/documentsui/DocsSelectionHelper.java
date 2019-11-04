/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

import java.util.Set;

/**
 * DocumentsUI SelectManager implementation that creates delegate instances
 * each time reset is called.
 */
public final class DocsSelectionHelper extends SelectionTracker<String> {

    private final DelegateFactory mFactory;

    // initialize to a dummy object incase we get some input
    // event drive calls before we're properly initialized.
    // See: b/69306667.
    private SelectionTracker<String> mDelegate = new DummySelectionTracker<>();

    @VisibleForTesting
    DocsSelectionHelper(DelegateFactory factory) {
        mFactory = factory;
    }

    public void reset(SelectionTracker<String> selectionTracker) {
        if (mDelegate != null) {
            mDelegate.clearSelection();
        }
        mDelegate = mFactory.create(selectionTracker);
    }

    @Override
    public void addObserver(SelectionObserver listener) {
        mDelegate.addObserver(listener);
    }

    @Override
    public boolean hasSelection() {
        return mDelegate.hasSelection();
    }

    @Override
    public Selection<String> getSelection() {
        return mDelegate.getSelection();
    }

    @Override
    public void copySelection(MutableSelection<String> dest) {
        mDelegate.copySelection(dest);
    }

    @Override
    public boolean isSelected(String id) {
        return mDelegate.isSelected(id);
    }

    @VisibleForTesting
    public void replaceSelection(Iterable<String> ids) {
        mDelegate.clearSelection();
        mDelegate.setItemsSelected(ids, true);
    }

    @Override
    public boolean setItemsSelected(Iterable<String> ids, boolean selected) {
        return mDelegate.setItemsSelected(ids, selected);
    }

    @Override
    public boolean clearSelection() {
        return mDelegate.clearSelection();
    }

    @Override
    public boolean select(String modelId) {
        return mDelegate.select(modelId);
    }

    @Override
    public boolean deselect(String modelId) {
        return mDelegate.deselect(modelId);
    }

    @Override
    public void startRange(int pos) {
        mDelegate.startRange(pos);
    }

    @Override
    public void extendRange(int pos) {
        mDelegate.extendRange(pos);
    }

    @Override
    public void endRange() {
        mDelegate.endRange();
    }

    @Override
    public boolean isRangeActive() {
        return mDelegate.isRangeActive();
    }

    @Override
    public void anchorRange(int position) {
        mDelegate.anchorRange(position);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        mDelegate.onSaveInstanceState(state);
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        mDelegate.onRestoreInstanceState(state);
    }

    // Below overridden protected methods are not used for delegation. These empty implementations
    // are just required by abstract declaration of parent class.
    @Override
    protected void restoreSelection(Selection<String> selection) {
    }

    @Override
    protected AdapterDataObserver getAdapterDataObserver() {
        return null;
    }

    @Override
    protected void extendProvisionalRange(int position) {
    }

    @Override
    protected void setProvisionalSelection(Set<String> newSelection) {
    }

    @Override
    protected void clearProvisionalSelection() {
    }

    @Override
    protected void mergeProvisionalSelection() {
    }

    public static DocsSelectionHelper create() {
        return new DocsSelectionHelper(DelegateFactory.INSTANCE);
    }

    /**
     * Use of a factory to create selection manager instances allows testable instances to
     * be inject from tests.
     */
    @VisibleForTesting
    static class DelegateFactory {
        static final DelegateFactory INSTANCE = new DelegateFactory();

        SelectionTracker<String> create(SelectionTracker<String> selectionTracker) {
            return selectionTracker;
        }
    }

    /**
     * Facilitates the use of ItemDetailsLookup.
     */
    public static abstract class DocDetailsLookup extends ItemDetailsLookup<String> {

        // Override as public for usages in other packages.
        @Override
        public boolean overItemWithSelectionKey(MotionEvent e) {
            return super.overItemWithSelectionKey(e);
        }
    }

    /**
     * Facilitates the use of stable ids.
     */
    public static abstract class StableIdProvider extends ItemKeyProvider<String> {

        protected StableIdProvider() {
            super(ItemKeyProvider.SCOPE_MAPPED);
        }
    }
}
