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

import static com.android.documentsui.StubProvider.ROOT_0_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.services.TestNotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
* This class test the below points
* - Delete large number of files
*/
@LargeTest
public class FileDeleteUiTest extends ActivityTest<FilesActivity> {
    private static final String TAG = "FileDeleteUiTest";

    private static final int DUMMY_FILE_COUNT = 1000;

    private static final int WAIT_TIME_SECONDS = 60;

    private final List<String> mCopyFileList = new ArrayList<String>();

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

    public FileDeleteUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set a flag to prevent many refreshes.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, bundle);

        try {
            bots.notifications.setNotificationAccess(getActivity(), true);
        } catch (Exception e) {
            Log.d(TAG, "Cannot set notification access. ", e);
        }

        initTestFiles();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TestNotificationService.ACTION_OPERATION_RESULT);
        context.registerReceiver(mReceiver, filter);
        context.sendBroadcast(new Intent(
                TestNotificationService.ACTION_CHANGE_EXECUTION_MODE));

        mOperationExecuted = false;
        mErrorReason = "No response from Notification";
        mCountDownLatch = new CountDownLatch(1);
    }

    @Override
    public void tearDown() throws Exception {
        mCountDownLatch.countDown();
        mCountDownLatch = null;

        context.unregisterReceiver(mReceiver);
        try {
            bots.notifications.setNotificationAccess(getActivity(), false);
        } catch (Exception e) {
            Log.d(TAG, "Cannot set notification access. ", e);
        }
        super.tearDown();
    }

    @Override
    public void initTestFiles() throws RemoteException {
        try {
            createDummyFiles();
        } catch (Exception e) {
            fail("Initialization failed");
        }
    }

    private void createDummyFiles() throws Exception {
        final ThreadPoolExecutor exec = new ThreadPoolExecutor(
                5, 5, 1000L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(100, true));
        for (int i = 0; i < DUMMY_FILE_COUNT; i++) {
            final String fileName = "file" + String.format("%04d", i) + ".log";
            if (exec.getQueue().size() >= 80) {
                Thread.sleep(50);
            }
            exec.submit(new Runnable() {
                @Override
                public void run() {
                    Uri uri = mDocsHelper.createDocument(rootDir0, "text/plain", fileName);
                    try {
                        mDocsHelper.writeDocument(uri, new byte[1]);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
            mCopyFileList.add(fileName);
        }
        exec.shutdown();
    }

    @HugeLongTest
    public void testDeleteAllDocument() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.main.clickToolbarOverflowItem(
                context.getResources().getString(R.string.menu_select_all));
        device.waitForIdle();

        bots.main.clickToolbarItem(R.id.action_menu_delete);
        bots.main.clickDialogOkButton();
        device.waitForIdle();

        try {
            mCountDownLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Cannot wait because of error." + e.toString());
        }

        assertTrue(mErrorReason, mOperationExecuted);

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();

        List<DocumentInfo> root1 = mDocsHelper.listChildren(rootDir0.documentId, 1000);
        assertTrue("Delete operation was not completed", root1.size() == 0);
    }
}
