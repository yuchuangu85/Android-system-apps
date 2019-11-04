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

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;

/**
 * A Ui test will do tests in the internal storage root. It is implemented because some operations
 * is failed and its result will different from the test on the StubProvider. b/115304092 is a
 * example which only happen on root from ExternalStorageProvidrer.
 */
@LargeTest
public class InternalStorageUiTest extends ActivityTest<FilesActivity> {

    private static final String fileName = "!Test3345678";
    private static final String newFileName = "!9527Test";
    private RootInfo rootPrimary;

    public InternalStorageUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mClient = mResolver.acquireUnstableContentProviderClient(Providers.AUTHORITY_STORAGE);
        mDocsHelper = new DocumentsProviderHelper(Providers.AUTHORITY_STORAGE, mClient);
        rootPrimary = mDocsHelper.getRoot(Providers.ROOT_ID_DEVICE);

        // If Internal Storage is not shown, turn on.
        State state = ((FilesActivity) getActivity()).getDisplayState();
        if (!state.showAdvanced) {
            bots.main.clickToolbarOverflowItem(
                    context.getResources().getString(R.string.menu_advanced_show));
        }

        bots.roots.openRoot(rootPrimary.title);
        deleteTestFiles();
    }

    @Override
    public void tearDown() throws Exception {
        deleteTestFiles();
        super.tearDown();
    }

    @HugeLongTest
    public void testRenameFile() throws Exception {
        createTestFiles();

        bots.directory.selectDocument(fileName);
        device.waitForIdle();

        bots.main.clickRename();

        bots.main.setDialogText(newFileName);
        device.waitForIdle();

        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsAbsent(fileName);
        bots.directory.assertDocumentsPresent(newFileName);
        // Snackbar will not show if no exception.
        assertNull(bots.directory.getSnackbar(context.getString(R.string.rename_error)));
    }

    private void createTestFiles() {
        mDocsHelper.createFolder(rootPrimary, fileName);
    }

    private void deleteTestFiles() throws Exception {
        boolean selected = false;
        // Delete the added file for not affect user and also avoid error on next test.
        if (bots.directory.hasDocuments(fileName)) {
            bots.directory.selectDocument(fileName);
            device.waitForIdle();
            selected = true;
        }
        if (bots.directory.hasDocuments(newFileName)) {
            bots.directory.selectDocument(newFileName);
            device.waitForIdle();
            selected = true;
        }
        if (selected) {
            bots.main.clickDelete();
            device.waitForIdle();
            bots.main.clickDialogOkButton();
        }
    }
}
