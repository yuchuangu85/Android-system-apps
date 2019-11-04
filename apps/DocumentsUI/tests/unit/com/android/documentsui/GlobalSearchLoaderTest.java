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

package com.android.documentsui;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.testing.ActivityManagers;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;
import com.android.documentsui.testing.TestImmediateExecutor;
import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class GlobalSearchLoaderTest {

    private static final String SEARCH_STRING = "freddy";
    private static int FILE_FLAG = Document.FLAG_SUPPORTS_MOVE | Document.FLAG_SUPPORTS_DELETE
            | Document.FLAG_SUPPORTS_REMOVE;

    private TestEnv mEnv;
    private TestActivity mActivity;
    private GlobalSearchLoader mLoader;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActivity.activityManager = ActivityManagers.create(false);

        mEnv.state.action = State.ACTION_BROWSE;
        mEnv.state.acceptMimes = new String[]{"*/*"};

        final Bundle queryArgs = new Bundle();
        queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, SEARCH_STRING);
        mLoader = new GlobalSearchLoader(mActivity, mEnv.providers, mEnv.state,
                TestImmediateExecutor.createLookup(), new TestFileTypeLookup(), queryArgs);

        final DocumentInfo doc = mEnv.model.createFile(SEARCH_STRING + ".jpg", FILE_FLAG);
        doc.lastModified = System.currentTimeMillis();
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(doc);

        TestProvidersAccess.HOME.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        mEnv.state.showAdvanced = true;
    }

    @After
    public void tearDown() {
        TestProvidersAccess.HOME.flags &= ~DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        mEnv.state.showAdvanced = false;
    }

    @Test
    public void testNotSearchableRoot_beIgnored() {
        TestProvidersAccess.PICKLES.flags |= DocumentsContract.Root.FLAG_LOCAL_ONLY;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.PICKLES));
        TestProvidersAccess.PICKLES.flags &= ~DocumentsContract.Root.FLAG_LOCAL_ONLY;
    }

    @Test
    public void testNotLocalOnlyRoot_beIgnored() {
        TestProvidersAccess.PICKLES.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.PICKLES));
        TestProvidersAccess.PICKLES.flags &= ~DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
    }

    @Test
    public void testShowAdvance_recentRoot_beIgnored() {
        TestProvidersAccess.IMAGE.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.IMAGE));
        TestProvidersAccess.IMAGE.flags &= ~DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
    }

    @Test
    public void testShowAdvance_imageRoot_beIgnored() {
        TestProvidersAccess.IMAGE.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.IMAGE));
        TestProvidersAccess.IMAGE.flags &= ~(DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY);
    }

    @Test
    public void testShowAdvance_videoRoot_beIgnored() {
        TestProvidersAccess.VIDEO.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.VIDEO));
        TestProvidersAccess.VIDEO.flags &= ~(DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY);
    }

    @Test
    public void testShowAdvance_audioRoot_beIgnored() {
        TestProvidersAccess.AUDIO.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.AUDIO));
        TestProvidersAccess.AUDIO.flags &= ~(DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
                | DocumentsContract.Root.FLAG_LOCAL_ONLY);
    }

    @Test
    public void testShowAdvance_downloadRoot_beIgnored() {
        TestProvidersAccess.DOWNLOADS.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        assertTrue(mLoader.shouldIgnoreRoot(TestProvidersAccess.DOWNLOADS));
        TestProvidersAccess.DOWNLOADS.flags &= ~DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
    }

    @Test
    public void testSearchResult_includeDirectory() {
        final DocumentInfo doc = mEnv.model.createFolder(SEARCH_STRING);
        doc.lastModified = System.currentTimeMillis();

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(doc);

        final DirectoryResult result = mLoader.loadInBackground();

        final Cursor c = result.cursor;
        assertEquals(1, c.getCount());

        c.moveToNext();
        final String mimeType = c.getString(c.getColumnIndex(Document.COLUMN_MIME_TYPE));
        assertEquals(Document.MIME_TYPE_DIR, mimeType);
    }

    @Test
    public void testSearchResult_isNotMovable() {
        final DirectoryResult result = mLoader.loadInBackground();

        final Cursor c = result.cursor;
        assertEquals(1, c.getCount());

        c.moveToNext();
        final int flags = c.getInt(c.getColumnIndex(Document.COLUMN_FLAGS));
        assertEquals(0, flags & Document.FLAG_SUPPORTS_DELETE);
        assertEquals(0, flags & Document.FLAG_SUPPORTS_REMOVE);
        assertEquals(0, flags & Document.FLAG_SUPPORTS_MOVE);
    }

    @Test
    public void testSearchResult_includeSearchString() {
        final DocumentInfo pdfDoc = mEnv.model.createFile(SEARCH_STRING + ".pdf");
        pdfDoc.lastModified = System.currentTimeMillis();

        final DocumentInfo apkDoc = mEnv.model.createFile(SEARCH_STRING + ".apk");
        apkDoc.lastModified = System.currentTimeMillis();

        final DocumentInfo testApkDoc = mEnv.model.createFile("test.apk");
        testApkDoc.lastModified = System.currentTimeMillis();

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(pdfDoc, apkDoc, testApkDoc);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        final DirectoryResult result = mLoader.loadInBackground();
        final Cursor c = result.cursor;

        assertEquals(2, c.getCount());

        c.moveToNext();
        String displayName = c.getString(c.getColumnIndex(Document.COLUMN_DISPLAY_NAME));
        assertTrue(displayName.contains(SEARCH_STRING));

        c.moveToNext();
        displayName = c.getString(c.getColumnIndex(Document.COLUMN_DISPLAY_NAME));
        assertTrue(displayName.contains(SEARCH_STRING));
    }

    @Test
    public void testSearchResult_includeDifferentRoot() {
        final DocumentInfo pdfDoc = mEnv.model.createFile(SEARCH_STRING + ".pdf");
        pdfDoc.lastModified = System.currentTimeMillis();

        final DocumentInfo apkDoc = mEnv.model.createFile(SEARCH_STRING + ".apk");
        apkDoc.lastModified = System.currentTimeMillis();

        final DocumentInfo testApkDoc = mEnv.model.createFile("test.apk");
        testApkDoc.lastModified = System.currentTimeMillis();

        mEnv.mockProviders.get(TestProvidersAccess.HAMMY.authority)
                .setNextChildDocumentsReturns(pdfDoc, apkDoc, testApkDoc);

        TestProvidersAccess.HAMMY.flags |= DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        final DirectoryResult result = mLoader.loadInBackground();
        final Cursor c = result.cursor;

        assertEquals(3, c.getCount());

        TestProvidersAccess.HAMMY.flags &= ~DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
    }
}
