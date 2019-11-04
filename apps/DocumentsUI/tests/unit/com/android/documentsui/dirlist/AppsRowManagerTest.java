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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.InstrumentationRegistry;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.R;
import com.android.documentsui.base.State;
import com.android.documentsui.sidebar.AppItem;
import com.android.documentsui.sidebar.Item;
import com.android.documentsui.sidebar.RootItem;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResolveInfo;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AppsRowManagerTest {

    private AppsRowManager mAppsRowManager;

    private ActionHandler mActionHandler;
    private BaseActivity mActivity;
    private State mState;

    private View mAppsRow;
    private LinearLayout mAppsGroup;

    @Before
    public void setUp() {
        mActionHandler = new TestActionHandler();

        mAppsRowManager = new AppsRowManager(mActionHandler);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        mState = new State();
        mActivity = mock(BaseActivity.class);
        mAppsRow = layoutInflater.inflate(R.layout.apps_row, null);
        mAppsGroup = mAppsRow.findViewById(R.id.apps_row);

        when(mActivity.getLayoutInflater()).thenReturn(layoutInflater);
        when(mActivity.getDisplayState()).thenReturn(mState);
        when(mActivity.findViewById(R.id.apps_row)).thenReturn(mAppsRow);
        when(mActivity.findViewById(R.id.apps_group)).thenReturn(mAppsGroup);
    }

    @Test
    public void testUpdateList_byRootItem() {
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, mActionHandler));
        rootList.add(new RootItem(TestProvidersAccess.PICKLES, mActionHandler));

        final List<AppsRowItemData> chipDataList = mAppsRowManager.updateList(rootList);

        assertEquals(chipDataList.size(), rootList.size());
        assertEquals(TestProvidersAccess.INSPECTOR.title, chipDataList.get(0).getTitle());
        assertFalse(chipDataList.get(0).showExitIcon());
        assertEquals(TestProvidersAccess.PICKLES.title, chipDataList.get(1).getTitle());
        assertFalse(chipDataList.get(1).showExitIcon());
    }

    @Test
    public void testUpdateList_byHybridItem() {
        final String testPackageName = "com.test1";
        final ResolveInfo info = TestResolveInfo.create();
        info.activityInfo.packageName = testPackageName;

        List<Item> hybridList = new ArrayList<>();
        hybridList.add(new RootItem(TestProvidersAccess.INSPECTOR, mActionHandler));
        hybridList.add(new AppItem(info, TestProvidersAccess.PICKLES.title, mActionHandler));

        final List<AppsRowItemData> chipDataList = mAppsRowManager.updateList(hybridList);

        assertEquals(chipDataList.size(), hybridList.size());
        assertEquals(TestProvidersAccess.INSPECTOR.title, chipDataList.get(0).getTitle());
        assertTrue(chipDataList.get(0) instanceof AppsRowItemData.RootData);
        assertFalse(chipDataList.get(0).showExitIcon());
        assertEquals(TestProvidersAccess.PICKLES.title, chipDataList.get(1).getTitle());
        assertTrue(chipDataList.get(1) instanceof AppsRowItemData.AppData);
        assertTrue(chipDataList.get(1).showExitIcon());
    }

    @Test
    public void testUpdateView_matchedState_showRow() {
        mState.action = State.ACTION_BROWSE;
        mState.stack.changeRoot(TestProvidersAccess.RECENTS);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, mActionHandler));
        mAppsRowManager.updateList(rootList);

        mAppsRowManager.updateView(mActivity);

        assertEquals(View.VISIBLE, mAppsRow.getVisibility());
        assertEquals(1, mAppsGroup.getChildCount());
    }

    @Test
    public void testUpdateView_notInRecent_hideRow() {
        mState.action = State.ACTION_BROWSE;
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, mActionHandler));
        mAppsRowManager.updateList(rootList);

        mState.stack.changeRoot(TestProvidersAccess.DOWNLOADS);

        mAppsRowManager.updateView(mActivity);

        assertEquals(View.GONE, mAppsRow.getVisibility());
    }

    @Test
    public void testUpdateView_notHandledAction_hideRow() {
        mState.action = State.ACTION_OPEN_TREE;

        mState.stack.changeRoot(TestProvidersAccess.RECENTS);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, mActionHandler));
        mAppsRowManager.updateList(rootList);

        mAppsRowManager.updateView(mActivity);

        assertEquals(View.GONE, mAppsRow.getVisibility());
    }

    @Test
    public void testUpdateView_noItems_hideRow() {
        mState.action = State.ACTION_BROWSE;
        mState.stack.changeRoot(TestProvidersAccess.RECENTS);

        final List<Item> rootList = new ArrayList<>();
        mAppsRowManager.updateList(rootList);

        mAppsRowManager.updateView(mActivity);

        assertEquals(View.GONE, mAppsRow.getVisibility());
    }
}

