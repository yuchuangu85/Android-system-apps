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

package com.android.documentsui.dirlist;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.SelectionHelpers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class KeyInputHandlerTest {

    private static final List<String> ITEMS = TestData.create(100);

    private KeyInputHandler mInputHandler;
    private SelectionTracker<String> mSelectionHelper;
    private TestFocusHandler mFocusHandler;
    private TestCallbacks mCallbacks;

    @Before
    public void setUp() {
        mSelectionHelper = SelectionHelpers.createTestInstance(ITEMS);
        mFocusHandler = new TestFocusHandler();
        mCallbacks = new TestCallbacks();

        mInputHandler = new KeyInputHandler(
                mSelectionHelper,
                SelectionHelpers.CAN_SET_ANYTHING,
                mCallbacks);
    }

    @Test
    public void testArrowKey_nonShiftClearsSelection() {
        mSelectionHelper.select("11");

        mFocusHandler.handleKey = true;
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP);
        mInputHandler.onKey(null, event.getKeyCode(), event);

        Selection<String> selection = mSelectionHelper.getSelection();
        assertEquals(selection.toString(), 0, selection.size());
    }

    private static final class TestCallbacks
            extends KeyInputHandler.Callbacks<DocumentItemDetails> {

        private @Nullable DocumentItemDetails mActivated;

        @Override
        public boolean isInteractiveItem(DocumentItemDetails item, KeyEvent e) {
            return true;
        }

        @Override
        public boolean onItemActivated(DocumentItemDetails item, KeyEvent e) {
            mActivated = item;
            return false;
        }

        private void assertActivated(DocumentItemDetails expected) {
            assertEquals(expected, mActivated);
        }

        @Override
        public boolean onFocusItem(DocumentItemDetails details, int keyCode, KeyEvent event) {
            return true;
        }
    }
}
