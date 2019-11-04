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

import android.database.Cursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.widget.Space;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.testing.TestRecyclerView;
import com.android.documentsui.testing.Views;

import java.util.ArrayList;
import java.util.List;

@SmallTest
public class AccessibilityTest extends AndroidTestCase {

    private static final List<String> ITEMS = TestData.create(10);

    private TestRecyclerView mView;
    private AccessibilityEventRouter mAccessibilityDelegate;
    private boolean mClickCallbackCalled = false;
    private boolean mLongClickCallbackCalled = false;

    @Override
    public void setUp() throws Exception {
        mView = TestRecyclerView.create(ITEMS);
        mAccessibilityDelegate = new AccessibilityEventRouter(mView, (View v) -> {
            mClickCallbackCalled = true;
            return true;
        }, (View v) -> {
            mLongClickCallbackCalled = true;
            return true;
        });
        mView.setAccessibilityDelegateCompat(mAccessibilityDelegate);
    }

    public void test_announceSelected() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertTrue(info.isSelected());
    }

    public void testNullItemDetails_NoActionClick() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();

        List<RecyclerView.ViewHolder> holders = new ArrayList<>();
        holders.add(new MessageHolder(mView.getContext(), new Space(mView.getContext())) {
            @Override
            public void bind(Cursor cursor, String modelId) {

            }
        });

        mView.setHolders(holders);

        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertFalse(info.isClickable());
    }

    public void test_routesAccessibilityClicks() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.getItemDelegate()
            .performAccessibilityAction(item, AccessibilityNodeInfoCompat.ACTION_CLICK, null);
        assertTrue(mClickCallbackCalled);
    }

    public void test_routesAccessibilityLongClicks() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.getItemDelegate()
            .performAccessibilityAction(item, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null);
        assertTrue(mLongClickCallbackCalled);
    }
}
