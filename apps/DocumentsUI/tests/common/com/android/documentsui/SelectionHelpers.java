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

package com.android.documentsui;

import androidx.recyclerview.selection.DefaultSelectionTracker;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate;
import androidx.recyclerview.selection.StorageStrategy;

import com.android.documentsui.testing.TestStableIdProvider;

import java.util.Collections;
import java.util.List;

public class SelectionHelpers {

    public static final SelectionPredicate<String> CAN_SET_ANYTHING =
            SelectionPredicates.createSelectAnything();

    private SelectionHelpers() {}

    public static DocsSelectionHelper createTestInstance() {
        return createTestInstance(Collections.emptyList());
    }

    public static DocsSelectionHelper createTestInstance(List<String> docs) {
        DocsSelectionHelper manager = new DocsSelectionHelper(
                new DocsSelectionHelper.DelegateFactory() {

                    @Override
                    SelectionTracker<String> create(SelectionTracker<String> selectionTracker) {
                        return new DefaultSelectionTracker<String>(
                                Integer.toHexString(System.identityHashCode(docs)),
                                new TestStableIdProvider(docs),
                                CAN_SET_ANYTHING,
                                StorageStrategy.createStringStorage());
                    }
                });

        manager.reset(null);
        return manager;
    }
}
