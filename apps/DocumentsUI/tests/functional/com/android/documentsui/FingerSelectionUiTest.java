/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;

@LargeTest
public class FingerSelectionUiTest extends ActivityTest<FilesActivity> {

    public FingerSelectionUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
        bots.roots.closeDrawer();
    }

    public void testFingerSelection_outOfRange() throws Exception {
        bots.main.switchToGridMode();
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect firstDoc = bots.directory.findDocument(fileName1).getBounds();
        // Start from list right bottom.
        Point start = new Point(firstDoc.centerX(), firstDoc.centerY());
        // End is center of file1
        Point end = new Point(dirListBounds.right, dirListBounds.bottom);
        bots.gesture.fingerSelection(start, end);

        bots.directory.assertSelection(3);
    }
}
