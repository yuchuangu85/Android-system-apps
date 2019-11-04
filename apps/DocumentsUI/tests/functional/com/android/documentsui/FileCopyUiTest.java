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

package com.android.documentsui;

import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;
import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;

import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.services.TestNotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
* This class test the below points
* - Copy large number of files on the internal/external storage
*/
@LargeTest
public class FileCopyUiTest extends ActivityTest<FilesActivity> {
    private static final String TAG = "FileCopyUiTest";

    private static final String TARGET_FOLDER = "test_folder";

    private static final int TARGET_COUNT = 1000;

    private static final int WAIT_TIME_SECONDS = 180;

    private final Map<String, Long> mTargetFileList = new HashMap<String, Long>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TestNotificationService.ACTION_OPERATION_RESULT.equals(action)) {
                mOperationExecuted = intent.getBooleanExtra(
                        TestNotificationService.EXTRA_RESULT, false);
                if (!mOperationExecuted) {
                    mErrorReason = intent.getStringExtra(
                            TestNotificationService.EXTRA_ERROR_REASON);
                }
                mCountDownLatch.countDown();
            }
        }
    };

    private CountDownLatch mCountDownLatch;

    private boolean mOperationExecuted;

    private String mErrorReason;

    private DocumentsProviderHelper mStorageDocsHelper;

    private RootInfo mPrimaryRoot;

    private RootInfo mSdCardRoot;

    private String mSdCardLabel;

    private boolean mIsVirtualSdCard;

    private int mPreTestStayAwakeValue;

    public FileCopyUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Create ContentProviderClient and DocumentsProviderHelper for using SD Card.
        ContentProviderClient storageClient =
                mResolver.acquireUnstableContentProviderClient(AUTHORITY_STORAGE);
        mStorageDocsHelper = new DocumentsProviderHelper(AUTHORITY_STORAGE, storageClient);

        // Set a flag to prevent many refreshes.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, bundle);

        // Set "Stay awake" until test is finished.
        mPreTestStayAwakeValue = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        device.executeShellCommand("settings put global stay_on_while_plugged_in 3");

        // If Internal Storage is not shown, turn on.
        State state = ((FilesActivity) getActivity()).getDisplayState();
        if (!state.showAdvanced) {
            bots.main.clickToolbarOverflowItem(
                    context.getResources().getString(R.string.menu_advanced_show));
        }

        try {
            bots.notifications.setNotificationAccess(getActivity(), true);
        } catch (Exception e) {
            Log.d(TAG, "Cannot set notification access. ", e);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(TestNotificationService.ACTION_OPERATION_RESULT);
        context.registerReceiver(mReceiver, filter);
        context.sendBroadcast(new Intent(
                TestNotificationService.ACTION_CHANGE_EXECUTION_MODE));

        mOperationExecuted = false;
        mErrorReason = "No response from Notification";

        initStorageRootInfo();
        assertNotNull("Internal Storage not found", mPrimaryRoot);

        // If SD Card is not found, enable Virtual SD Card
        if (mSdCardRoot == null) {
            mIsVirtualSdCard = enableVirtualSdCard();
            assertTrue("Cannot set virtual SD Card", mIsVirtualSdCard);
            // Call initStorageRootInfo() again for setting SD Card root
            initStorageRootInfo();
        }
    }

    @Override
    public void tearDown() throws Exception {
        // Delete created files
        deleteDocuments(Build.MODEL);
        deleteDocuments(mSdCardLabel);

        if (mIsVirtualSdCard) {
            device.executeShellCommand("sm set-virtual-disk false");
        }

        device.executeShellCommand("settings put global stay_on_while_plugged_in "
                + mPreTestStayAwakeValue);

        context.unregisterReceiver(mReceiver);
        try {
            bots.notifications.setNotificationAccess(getActivity(), false);
        } catch (Exception e) {
            Log.d(TAG, "Cannot set notification access. ", e);
        }

        super.tearDown();
    }

    private boolean createDocuments(String label, RootInfo root,
            DocumentsProviderHelper helper) throws Exception {
        if (TextUtils.isEmpty(label) || root == null) {
            return false;
        }

        // If Test folder is already created, delete it
        if (bots.directory.hasDocuments(TARGET_FOLDER)) {
            deleteDocuments(label);
        }

        // Create folder and create file in its folder
        bots.roots.openRoot(label);
        Uri uri = helper.createFolder(root, TARGET_FOLDER);
        device.waitForIdle();
        if (!bots.directory.hasDocuments(TARGET_FOLDER)) {
            return false;
        }

        loadImages(uri, helper);

        // Check that image files are loaded completely
        DocumentInfo parent = helper.findDocument(root.documentId, TARGET_FOLDER);
        List<DocumentInfo> children = helper.listChildren(parent.documentId, TARGET_COUNT);
        for (DocumentInfo docInfo : children) {
            mTargetFileList.put(docInfo.displayName, docInfo.size);
        }
        assertTrue("Lack of loading file. File count = " + mTargetFileList.size(),
                mTargetFileList.size() == TARGET_COUNT);

        return true;
    }

    private boolean deleteDocuments(String label) throws Exception {
        if (TextUtils.isEmpty(label)) {
            return false;
        }

        bots.roots.openRoot(label);
        if (!bots.directory.hasDocuments(TARGET_FOLDER)) {
            return true;
        }

        bots.directory.selectDocument(TARGET_FOLDER, 1);
        device.waitForIdle();

        bots.main.clickToolbarItem(R.id.action_menu_delete);
        bots.main.clickDialogOkButton();
        device.waitForIdle();

        bots.directory.findDocument(TARGET_FOLDER).waitUntilGone(WAIT_TIME_SECONDS);
        return !bots.directory.hasDocuments(TARGET_FOLDER);
    }

    private void loadImages(Uri root, DocumentsProviderHelper helper) throws Exception {
        Context testContext = getInstrumentation().getContext();
        Resources res = testContext.getResources();
        try {
            int resId = res.getIdentifier(
                    "uitest_images", "raw", testContext.getPackageName());
            loadImageFromResources(root, helper, resId, res);
        } catch (Exception e) {
            Log.d(TAG, "Error occurs when loading image. ", e);
        }
    }

    private void loadImageFromResources(Uri root, DocumentsProviderHelper helper, int resId,
            Resources res) throws Exception {
        ZipInputStream in = null;
        int read = 0;
        int count = 0;
        try {
            in = new ZipInputStream(res.openRawResource(resId));
            ZipEntry archiveEntry = null;
            while ((archiveEntry = in.getNextEntry()) != null && (count++ < TARGET_COUNT)) {
                String fileName = archiveEntry.getName();
                Uri uri = helper.createDocument(root, "image/png", fileName);
                byte[] buff = new byte[1024];
                while ((read = in.read(buff)) > 0) {
                    helper.writeAppendDocument(uri, buff);
                }
                buff = null;
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                    in  = null;
                } catch (Exception e) {
                    Log.d(TAG, "Error occurs when close ZipInputStream. ", e);
                }
            }
        }
    }

    /** @return true if virtual SD Card setting is completed. Othrewise false */
    private boolean enableVirtualSdCard() throws Exception {
        boolean result = false;
        try {
            device.executeShellCommand("sm set-virtual-disk true");
            String diskId = getAdoptionDisk();
            assertNotNull("Failed to setup virtual disk.", diskId);
            device.executeShellCommand(String.format("sm partition %s public", diskId));
            result = waitForPublicVolume();
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private String getAdoptionDisk() throws Exception {
        int attempt = 0;
        String disks = device.executeShellCommand("sm list-disks adoptable");
        while ((disks == null || disks.isEmpty()) && attempt++ < 15) {
            SystemClock.sleep(1000);
            disks = device.executeShellCommand("sm list-disks adoptable");
        }

        if (disks == null || disks.isEmpty()) {
            return null;
        }
        return disks.split("\n")[0].trim();
    }

    private boolean waitForPublicVolume() throws Exception {
        int attempt = 0;
        String volumes = device.executeShellCommand("sm list-volumes public");
        while ((volumes == null || volumes.isEmpty() || !volumes.contains("mounted"))
                && attempt++ < 15) {
            SystemClock.sleep(1000);
            volumes = device.executeShellCommand("sm list-volumes public");
        }

        if (volumes == null || volumes.isEmpty()) {
            return false;
        }
        return true;
    }

    private void initStorageRootInfo() throws RemoteException {
        List<RootInfo> rootList = mStorageDocsHelper.getRootList();
        for (RootInfo info : rootList) {
             if (ROOT_ID_DEVICE.equals(info.rootId)) {
                 mPrimaryRoot = info;
             } else if (info.isSd()) {
                 mSdCardRoot = info;
                 mSdCardLabel = info.title;
             }
        }
    }

    private void copyFiles(String sourceRoot, String targetRoot) throws Exception {
        mCountDownLatch = new CountDownLatch(1);
        // Copy folder and child files
        bots.roots.openRoot(sourceRoot);
        bots.directory.selectDocument(TARGET_FOLDER, 1);
        device.waitForIdle();
        bots.main.clickToolbarOverflowItem(context.getResources().getString(R.string.menu_copy));
        device.waitForIdle();
        bots.roots.openRoot(targetRoot);
        bots.main.clickDialogOkButton();
        device.waitForIdle();

        // Wait until copy operation finished
        try {
            mCountDownLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Cannot wait because of error." + e.toString());
        }

        assertTrue(mErrorReason, mOperationExecuted);
    }

    private void assertFilesCopied(String rootLabel, RootInfo rootInfo,
            DocumentsProviderHelper helper) throws Exception {
        // Check that copied folder exists
        bots.roots.openRoot(rootLabel);
        device.waitForIdle();
        bots.directory.assertDocumentsPresent(TARGET_FOLDER);

        // Check that copied files exist
        DocumentInfo parent = helper.findDocument(rootInfo.documentId, TARGET_FOLDER);
        List<DocumentInfo> children = helper.listChildren(parent.documentId, TARGET_COUNT);
        for (DocumentInfo info : children) {
            Long size = mTargetFileList.get(info.displayName);
            assertNotNull("Cannot find file.", size);
            assertTrue("Copied file contents differ.", info.size == size);
        }
    }

    // Copy Internal Storage -> Internal Storage //
    @HugeLongTest
    public void testCopyDocuments_InternalStorage() throws Exception {
        createDocuments(StubProvider.ROOT_0_ID, rootDir0, mDocsHelper);
        copyFiles(StubProvider.ROOT_0_ID, StubProvider.ROOT_1_ID);

        // Check that original folder exists
        bots.roots.openRoot(StubProvider.ROOT_0_ID);
        bots.directory.assertDocumentsPresent(TARGET_FOLDER);

        // Check that copied files exist
        assertFilesCopied(StubProvider.ROOT_1_ID, rootDir1, mDocsHelper);
    }

    // Copy SD Card -> Internal Storage //
    @HugeLongTest
    public void testCopyDocuments_FromSdCard() throws Exception {
        createDocuments(mSdCardLabel, mSdCardRoot, mStorageDocsHelper);
        copyFiles(mSdCardLabel, Build.MODEL);

        // Check that original folder exists
        bots.roots.openRoot(mSdCardLabel);
        bots.directory.assertDocumentsPresent(TARGET_FOLDER);

        // Check that copied files exist
        assertFilesCopied(Build.MODEL, mPrimaryRoot, mStorageDocsHelper);
    }

    // Copy Internal Storage -> SD Card //
    @HugeLongTest
    public void testCopyDocuments_ToSdCard() throws Exception {
        createDocuments(Build.MODEL, mPrimaryRoot, mStorageDocsHelper);
        copyFiles(Build.MODEL, mSdCardLabel);

        // Check that original folder exists
        bots.roots.openRoot(Build.MODEL);
        bots.directory.assertDocumentsPresent(TARGET_FOLDER);

        // Check that copied files exist
        assertFilesCopied(mSdCardLabel, mSdCardRoot, mStorageDocsHelper);
    }

    @HugeLongTest
    public void testCopyDocuments_documentsDisabled() throws Exception {
        mDocsHelper.createDocument(rootDir0, "text/plain", fileName1);
        bots.roots.openRoot(StubProvider.ROOT_0_ID);
        bots.directory.selectDocument(fileName1, 1);
        device.waitForIdle();
        bots.main.clickToolbarOverflowItem(context.getResources().getString(R.string.menu_copy));
        device.waitForIdle();
        bots.roots.openRoot(StubProvider.ROOT_0_ID);
        device.waitForIdle();

        assertFalse(bots.directory.findDocument(fileName1).isEnabled());
    }
}
