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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.DocsSelectionHelper.DelegateFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the specialized behaviors provided by DocsSelectionManager.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DocsSelectionHelperTest {

    private DocsSelectionHelper mSelectionMgr;
    private List<TestSelectionManager> mCreated;
    private DelegateFactory mFactory;

    @Before
    public void setup() {
        mCreated = new ArrayList<>();
        mFactory = new DelegateFactory() {

            @Override
            TestSelectionManager create(SelectionTracker<String> selectionTracker) {
                TestSelectionManager mgr = new TestSelectionManager();
                mCreated.add(mgr);
                return mgr;
            }
        };

        mSelectionMgr = new DocsSelectionHelper(mFactory);
    }

    @Test
    public void testCallableBeforeReset() {
        mSelectionMgr.hasSelection();
        assertNotNull(mSelectionMgr.getSelection());
        assertFalse(mSelectionMgr.isSelected("poodle"));
    }

    @Test
    public void testReset_CreatesNewInstances() {
        resetSelectionHelper();
        resetSelectionHelper();

        assertCreated(2);
    }

    @Test
    public void testReset_ClearsPreviousSelection() {
        resetSelectionHelper();
        resetSelectionHelper();

        mCreated.get(0).assertCleared(true);
        mCreated.get(1).assertCleared(false);
    }

    @Test
    public void testReplaceSelection() {
        resetSelectionHelper();

        List<String> ids = new ArrayList<>();
        ids.add("poodles");
        ids.add("hammy");
        mSelectionMgr.replaceSelection(ids);
        mCreated.get(0).assertCleared(true);
        mCreated.get(0).assertSelected("poodles", "hammy");
    }

    void assertCreated(int count) {
        assertEquals(count, mCreated.size());
    }

    private void resetSelectionHelper() {
        mSelectionMgr.reset(null); // nulls are passed to factory. We ignore.
    }

    private static final class TestSelectionManager extends DummySelectionTracker<String> {

        private boolean mCleared;
        private Map<String, Boolean> mSelected = new HashMap<>();

        void assertCleared(boolean expected) {
            assertEquals(expected, mCleared);
        }

        void assertSelected(String... expected) {
            for (String id : expected) {
                assertTrue(mSelected.containsKey(id));
                assertTrue(mSelected.get(id));
            }
            assertEquals(expected.length, mSelected.size());
        }

        @Override
        public void addObserver(SelectionObserver listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasSelection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Selection<String> getSelection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSelected(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restoreSelection(Selection<String> other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setItemsSelected(Iterable<String> ids, boolean selected) {
            for (String id : ids) {
                mSelected.put(id, selected);
            }
            return true;
        }

        @Override
        public boolean clearSelection() {
            mCleared = true;
            return true;
        }

        @Override
        public boolean select(String itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deselect(String itemId) {
            throw new UnsupportedOperationException();
        }
    }
}
