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

package com.android.documentsui.picker;

import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;

import androidx.fragment.app.FragmentActivity;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.Injector;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ActionType;
import com.android.documentsui.picker.ActionHandler.Addons;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestLastAccessedStorage;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResolveInfo;

import java.util.concurrent.Executor;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestableActionHandler<TestActivity> mHandler;
    private TestLastAccessedStorage mLastAccessed;
    private PickCountRecordStorage mPickCountRecord;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mEnv.providers.configurePm(mActivity.packageMgr);
        mEnv.injector.pickResult = new PickResult();
        mLastAccessed = new TestLastAccessedStorage();
        mPickCountRecord = mock(PickCountRecordStorage.class);

        mHandler = new TestableActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mEnv.injector,
                mLastAccessed,
                mPickCountRecord
        );

        mEnv.dialogs.confirmNext();

        mEnv.selectionMgr.select("1");

        AsyncTask.setDefaultExecutor(mEnv.mExecutor);
    }

    private static class TestableActionHandler<T extends FragmentActivity & Addons>
        extends ActionHandler {

        private UpdatePickResultTask mTask;

        TestableActionHandler(
            T activity,
            State state,
            ProvidersAccess providers,
            DocumentsAccess docs,
            SearchViewManager searchMgr,
            Lookup<String, Executor> executors,
            Injector injector,
            LastAccessedStorage lastAccessed,
            PickCountRecordStorage pickCountRecordStorage) {
            super(activity, state, providers, docs, searchMgr, executors, injector, lastAccessed);
            mTask = new UpdatePickResultTask(
                mActivity, mInjector.pickResult, pickCountRecordStorage);
        }

        @Override
        public UpdatePickResultTask getUpdatePickResultTask() {
            return mTask;
        }
    }

    @AfterClass
    public static void tearDownOnce() {
        AsyncTask.setDefaultExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Test
    public void testInitLocation_RestoresIfStackIsLoaded() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.DOWNLOADS);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mHandler.initLocation(mActivity.getIntent());
        mActivity.restoreRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_LoadsRootDocIfStackOnlyHasRoot() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HAMMY);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.HAMMY.getUri());
    }

    @Test
    public void testInitLocation_CopyDestination_DefaultsToDownloads() throws Exception {
        mActivity.resources.bools.put(R.bool.show_documents_root, false);

        Intent intent = mActivity.getIntent();
        intent.setAction(Shared.ACTION_PICK_COPY_DESTINATION);
        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_CopyDestination_DocumentsRootEnabled() throws Exception {
        mActivity.resources.bools.put(R.bool.show_documents_root, true);
        mActivity.resources.strings.put(R.string.default_root_uri, TestProvidersAccess.HOME.getUri().toString());

        Intent intent = mActivity.getIntent();
        intent.setAction(Shared.ACTION_PICK_COPY_DESTINATION);
        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.HOME.getUri());
    }

    @Test
    public void testInitLocation_LaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, TestEnv.FILE_GIF.derivedUri);
        mHandler.initLocation(intent);

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_RestoresLastAccessedStack() throws Exception {
        final DocumentStack stack =
                new DocumentStack(TestProvidersAccess.HAMMY, TestEnv.FOLDER_0, TestEnv.FOLDER_1);
        mLastAccessed.setLastAccessed(mActivity, stack);

        mHandler.initLocation(mActivity.getIntent());

        mEnv.beforeAsserts();
        assertEquals(stack, mEnv.state.stack);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_DefaultToRecents_ActionGetContent() throws Exception {
        testInitLocationDefaultToRecentsOnAction(State.ACTION_GET_CONTENT);
    }

    @Test
    public void testInitLocation_DefaultToRecents_ActionOpen() throws Exception {
        testInitLocationDefaultToRecentsOnAction(State.ACTION_OPEN);
    }

    @Test
    public void testInitLocation_DefaultToDownloads_ActionOpenTree() throws Exception {
        testInitLocationDefaultToDownloadsOnAction(State.ACTION_OPEN_TREE);
    }

    @Test
    public void testInitLocation_DefaultsToDownloads_ActionCreate() throws Exception {
        testInitLocationDefaultToDownloadsOnAction(State.ACTION_CREATE);
    }

    @Test
    public void testOpenContainerDocument() {
        mHandler.openContainerDocument(TestEnv.FOLDER_0);

        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.peek());

        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testIncreasePickCountRecordCalled() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        mActivity.finishedHandler.assertNotCalled();
        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri);

        mEnv.beforeAsserts();

        verify(mPickCountRecord).increasePickCountRecord(
            mActivity.getApplicationContext(), TestEnv.FILE_JPG.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testPickDocument_SetsCorrectResultAndFinishes_ActionPickCopyDestination()
            throws Exception {

        mEnv.state.action = State.ACTION_PICK_COPY_DESTINATION;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);
        mEnv.state.stack.push(TestEnv.FOLDER_2);

        mActivity.finishedHandler.assertNotCalled();

        mHandler.pickDocument(null, TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FOLDER_2.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testPickDocument_SetsCorrectResultAndFinishes_ActionOpenTree() throws Exception {
        mEnv.state.action = State.ACTION_OPEN_TREE;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);
        mEnv.state.stack.push(TestEnv.FOLDER_2);

        mActivity.finishedHandler.assertNotCalled();

        Uri uri = DocumentsContract.buildTreeDocumentUri(
                TestEnv.FOLDER_2.authority, TestEnv.FOLDER_2.documentId);
        mHandler.finishPicking(uri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, true);
        assertContent(result, DocumentsContract.buildTreeDocumentUri(
                TestProvidersAccess.HOME.authority, TestEnv.FOLDER_2.documentId));

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testSaveDocument_SetsCorrectResultAndFinishes() throws Exception {
        mEnv.state.action = State.ACTION_CREATE;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        final String mimeType = "audio/aac";
        final String displayName = "foobar.m4a";

        mHandler.saveDocument(mimeType, displayName, (boolean inProgress) -> {});

        mEnv.beforeAsserts();

        mEnv.docs.assertCreatedDocument(TestEnv.FOLDER_1, mimeType, displayName);
        final Uri docUri = mEnv.docs.getLastCreatedDocumentUri();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, docUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testSaveDocument_ConfirmsOverwrite() {
        if (!mEnv.features.isOverwriteConfirmationEnabled()) {
            return;
        }

        mEnv.state.action = State.ACTION_CREATE;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        mHandler.saveDocument(null, TestEnv.FILE_JPG);

        mEnv.dialogs.assertOverwriteConfirmed(TestEnv.FILE_JPG);
    }

    @Test
    public void testPickDocument_ConfirmsOpenTree() {
        mEnv.state.action = State.ACTION_OPEN_TREE;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);

        mHandler.pickDocument(null, TestEnv.FOLDER_1);

        mEnv.dialogs.assertDocumentTreeConfirmed(TestEnv.FOLDER_1);
    }

    @Test
    public void testFinishPicking_SetsCorrectResultAndFinishes_ActionGetContent() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        mActivity.finishedHandler.assertNotCalled();

        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FILE_JPG.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testFinishPicking_SetsCorrectResultAndFinishes_ActionGetContent_MultipleSelection()
            throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);
        mEnv.state.acceptMimes = new String[] { "image/*" };

        mActivity.finishedHandler.assertNotCalled();

        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri, TestEnv.FILE_GIF.derivedUri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, false);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FILE_JPG.derivedUri, TestEnv.FILE_GIF.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testFinishPicking_SetsCorrectResultAndFinishes_ActionOpen() throws Exception {
        mEnv.state.action = State.ACTION_OPEN;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        mActivity.finishedHandler.assertNotCalled();

        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FILE_JPG.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testFinishPicking_SetsCorrectResultAndFinishes_ActionOpen_MultipleSelection()
            throws Exception {
        mEnv.state.action = State.ACTION_OPEN;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);
        mEnv.state.acceptMimes = new String[] { "image/*" };

        mActivity.finishedHandler.assertNotCalled();

        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri, TestEnv.FILE_GIF.derivedUri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FILE_JPG.derivedUri, TestEnv.FILE_GIF.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testFinishPicking_SetsCorrectResultAndFinishes_ActionCreate() throws Exception {
        mEnv.state.action = State.ACTION_CREATE;
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_1);

        mActivity.finishedHandler.assertNotCalled();

        mHandler.finishPicking(TestEnv.FILE_JPG.derivedUri);

        mEnv.beforeAsserts();

        assertLastAccessedStackUpdated();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        final Intent result = mActivity.setResult.getLastValue().second;
        assertPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
        assertPermission(result, Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, false);
        assertContent(result, TestEnv.FILE_JPG.derivedUri);

        mActivity.finishedHandler.assertCalled();
    }

    @Test
    public void testOnAppPickedResult_OnOK() throws Exception {
        Intent intent = new Intent();
        mHandler.onActivityResult(AbstractActionHandler.CODE_FORWARD, Activity.RESULT_OK, intent);
        mActivity.finishedHandler.assertCalled();
        mActivity.setResult.assertCalled();

        assertEquals(Activity.RESULT_OK, (long) mActivity.setResult.getLastValue().first);
        assertEquals(intent, mActivity.setResult.getLastValue().second);
    }

    @Test
    public void testOnAppPickedResult_OnNotOK() throws Exception {
        Intent intent = new Intent();
        mHandler.onActivityResult(0, Activity.RESULT_OK, intent);
        mActivity.finishedHandler.assertNotCalled();
        mActivity.setResult.assertNotCalled();

        mHandler.onActivityResult(AbstractActionHandler.CODE_FORWARD, Activity.RESULT_CANCELED,
                intent);
        mActivity.finishedHandler.assertNotCalled();
        mActivity.setResult.assertNotCalled();
    }

    @Test
    public void testOpenAppRoot() throws Exception {
        mHandler.openRoot(TestResolveInfo.create());
        assertEquals((long) mActivity.startActivityForResult.getLastValue().second,
                AbstractActionHandler.CODE_FORWARD);
        assertNotNull(mActivity.startActivityForResult.getLastValue().first);
    }

    @Test
    public void testOpenAppRootWithQueryContent_matchedContent() throws Exception {
        final String queryContent = "query";
        mActivity.intent.putExtra(Intent.EXTRA_CONTENT_QUERY, queryContent);
        mHandler.openRoot(TestResolveInfo.create());
        assertEquals(queryContent,
                mActivity.startActivityForResult.getLastValue().first.getStringExtra(
                        Intent.EXTRA_CONTENT_QUERY));
    }

    @Test
    public void testPreviewItem() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.priviewDocument(TestEnv.FILE_GIF);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    private void testInitLocationDefaultToRecentsOnAction(@ActionType int action)
            throws Exception {
        mEnv.state.action = action;

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();

        mHandler.initLocation(mActivity.getIntent());

        mEnv.beforeAsserts();
        assertEquals(TestProvidersAccess.RECENTS, mEnv.state.stack.getRoot());
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    private void testInitLocationDefaultToDownloadsOnAction(@ActionType int action)
            throws Exception {
        mEnv.state.action = action;
        mActivity.resources.bools.put(R.bool.show_documents_root, false);
        mActivity.resources.strings.put(R.string.default_root_uri,
                TestProvidersAccess.DOWNLOADS.getUri().toString());

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();

        mHandler.initLocation(mActivity.getIntent());

        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }

    private void assertLastAccessedStackUpdated() {
        assertEquals(mEnv.state.stack, mLastAccessed.getLastAccessed(
                mActivity, mEnv.providers, mEnv.state));
    }

    private void assertPermission(Intent intent, int permission, boolean granted) {
        int flags = intent.getFlags();

        if (granted) {
            assertEquals(permission, flags & permission);
        } else {
            assertEquals(0, flags & permission);
        }
    }

    private void assertContent(Intent intent, Uri... contents) {
        if (contents.length == 1) {
            assertEquals(contents[0], intent.getData());
        } else {
            ClipData clipData = intent.getClipData();

            assertNotNull(clipData);
            for (int i = 0; i < mEnv.state.acceptMimes.length; ++i) {
                assertEquals(mEnv.state.acceptMimes[i], clipData.getDescription().getMimeType(i));
            }
            for (int i = 0; i < contents.length; ++i) {
                assertEquals(contents[i], clipData.getItemAt(i).getUri());
            }
        }
    }
}
