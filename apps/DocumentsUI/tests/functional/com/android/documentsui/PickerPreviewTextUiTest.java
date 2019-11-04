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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.provider.DocumentsContract;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.Shared;
import com.android.documentsui.bots.UiBot;
import com.android.documentsui.picker.PickActivity;

@LargeTest
public class PickerPreviewTextUiTest extends ActivityTest<PickActivity>{

    private boolean mHasQuickViewer;

    public PickerPreviewTextUiTest() {
        super(PickActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();

        mHasQuickViewer = Shared.hasQuickViewer(context);
    }

    @Override
    protected void launchActivity() {
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(
                UiBot.targetPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        if (getInitialRoot() != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getInitialRoot().getUri());
        }
        intent.setType("text/*");
        setActivityIntent(intent);
        getActivity();  // Launch the activity.
    }

    public void testPreviewInvisible_disabled_gridMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToGridMode();
        assertFalse(bots.directory.findDocument(fileName2).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(fileName2));
    }

    public void testPreviewInvisible_disabled_listMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToListMode();
        assertFalse(bots.directory.findDocument(fileName2).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(fileName2));
    }

    public void testPreviewInvisible_directory_gridMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToGridMode();
        assertTrue(bots.directory.findDocument(dirName1).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(dirName1));
    }

    public void testPreviewInvisible_directory_listMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument(dirName1).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(dirName1));
    }

    public void testPreviewVisible_enabled_gridMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToGridMode();
        assertTrue(bots.directory.findDocument(fileName1).isEnabled());
        assertTrue(bots.directory.hasDocumentPreview(fileName1));
    }

    public void testPreviewVisible_enabled_listMode() throws Exception {
        if (!mHasQuickViewer) {
            return;
        }

        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument(fileName1).isEnabled());
        assertTrue(bots.directory.hasDocumentPreview(fileName1));
    }
}
