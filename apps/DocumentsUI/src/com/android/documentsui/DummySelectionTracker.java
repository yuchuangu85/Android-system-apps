/*
 * Copyright 2018 The Android Open Source Project
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

import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

import java.util.Set;

/**
 * A dummy SelectionTracker used by DocsSelectionHelper before a real SelectionTracker has been
 * initialized by DirectoryFragment.
 */
public class DummySelectionTracker<K> extends SelectionTracker<K> {

    @Override
    public void addObserver(SelectionObserver observer) {
    }

    @Override
    public boolean hasSelection() {
        return false;
    }

    @Override
    public Selection<K> getSelection() {
        return new MutableSelection<K>();
    }

    @Override
    public void copySelection(MutableSelection<K> dest) {
    }

    @Override
    public boolean isSelected(K key) {
        return false;
    }

    @Override
    public void restoreSelection(Selection<K> selection) {
    }

    @Override
    public boolean clearSelection() {
        return false;
    }

    @Override
    public boolean setItemsSelected(Iterable<K> keys, boolean selected) {
        return false;
    }

    @Override
    public boolean select(K key) {
        return false;
    }

    @Override
    public boolean deselect(K key) {
        return false;
    }

    @Override
    protected AdapterDataObserver getAdapterDataObserver() {
        return null;
    }

    @Override
    public void startRange(int position) {
    }

    @Override
    public void extendRange(int position) {
    }

    @Override
    public void endRange() {
    }

    @Override
    public boolean isRangeActive() {
        return false;
    }

    @Override
    public void anchorRange(int position) {
    }

    @Override
    public void extendProvisionalRange(int position) {
    }

    @Override
    public void setProvisionalSelection(Set<K> newSelection) {
    }

    @Override
    public void clearProvisionalSelection() {
    }

    @Override
    public void mergeProvisionalSelection() {
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
    }

}
