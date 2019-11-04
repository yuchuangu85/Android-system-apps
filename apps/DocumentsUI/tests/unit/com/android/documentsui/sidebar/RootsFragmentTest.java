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

package com.android.documentsui.sidebar;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

import android.content.pm.ResolveInfo;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResolveInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An unit test for RootsFragment.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class RootsFragmentTest {

    private RootsFragment mRootsFragment;

    private static final String[] EXPECTED_SORTED_RESULT = {
            TestProvidersAccess.RECENTS.title,
            TestProvidersAccess.IMAGE.title,
            TestProvidersAccess.VIDEO.title,
            TestProvidersAccess.AUDIO.title,
            TestProvidersAccess.DOWNLOADS.title,
            "" /* SpacerItem */,
            TestProvidersAccess.EXTERNALSTORAGE.title,
            TestProvidersAccess.HAMMY.title,
            "" /* SpacerItem */,
            TestProvidersAccess.INSPECTOR.title,
            TestProvidersAccess.PICKLES.title};

    @Before
    public void setUp() {
        mRootsFragment = new RootsFragment();
    }

    @Test
    public void testSortLoadResult_WithCorrectOrder() {
        List<Item> items = mRootsFragment.sortLoadResult(createFakeRootInfoList(),
                null /* excludePackage */, null /* handlerAppIntent */, new TestProvidersAccess());
        assertTrue(assertSortedResult(items));
    }

    @Test
    public void testItemComparator_WithCorrectOrder() {
        final String testPackageName = "com.test1";
        final String errorTestPackageName = "com.test2";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.HAMMY, null /* actionHandler */,
                errorTestPackageName));
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, null /* actionHandler */,
                errorTestPackageName));
        rootList.add(new RootItem(TestProvidersAccess.PICKLES, null /* actionHandler */,
                testPackageName));
        Collections.sort(rootList, comp);

        assertEquals(rootList.get(0).title, TestProvidersAccess.PICKLES.title);
        assertEquals(rootList.get(1).title, TestProvidersAccess.HAMMY.title);
        assertEquals(rootList.get(2).title, TestProvidersAccess.INSPECTOR.title);
    }

    @Test
    public void testItemComparator_differentItemTypes_WithCorrectOrder() {
        final String testPackageName = "com.test1";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.HAMMY, null /* actionHandler */,
                testPackageName));

        final ResolveInfo info = TestResolveInfo.create();
        info.activityInfo.packageName = testPackageName;

        rootList.add(new AppItem(info, TestProvidersAccess.PICKLES.title,
                null /* actionHandler */));
        rootList.add(new RootAndAppItem(TestProvidersAccess.INSPECTOR, info,
                null /* actionHandler */));

        Collections.sort(rootList, comp);

        assertEquals(rootList.get(0).title, TestProvidersAccess.HAMMY.title);
        assertEquals(rootList.get(1).title, TestProvidersAccess.INSPECTOR.title);
        assertEquals(rootList.get(2).title, TestProvidersAccess.PICKLES.title);
    }

    private boolean assertSortedResult(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item instanceof RootItem) {
                assertEquals(EXPECTED_SORTED_RESULT[i], ((RootItem) item).root.title);
            } else if (item instanceof SpacerItem) {
                assertTrue(EXPECTED_SORTED_RESULT[i].isEmpty());
            } else {
                return false;
            }
        }
        return true;
    }

    private List<RootInfo> createFakeRootInfoList() {
        final List<RootInfo> fakeRootInfoList = new ArrayList<>();
        fakeRootInfoList.add(TestProvidersAccess.PICKLES);
        fakeRootInfoList.add(TestProvidersAccess.HAMMY);
        fakeRootInfoList.add(TestProvidersAccess.INSPECTOR);
        fakeRootInfoList.add(TestProvidersAccess.DOWNLOADS);
        fakeRootInfoList.add(TestProvidersAccess.AUDIO);
        fakeRootInfoList.add(TestProvidersAccess.VIDEO);
        fakeRootInfoList.add(TestProvidersAccess.RECENTS);
        fakeRootInfoList.add(TestProvidersAccess.IMAGE);
        fakeRootInfoList.add(TestProvidersAccess.EXTERNALSTORAGE);
        return fakeRootInfoList;
    }
}
