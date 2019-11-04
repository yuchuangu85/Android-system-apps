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

import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_METADATA;
import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.media.ExifInterface;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;

import java.io.FileNotFoundException;

/**
 * Content Provider for testing the Document Inspector.
 *
 *  Structure of the provider.
 *
 *         Top ------------> Middle  ------> Bottom -------> Dummy21 50B
 *         openInProvider    Dummy1 50B      Dummy11 50B     Dummy22 150B
 *         test.txt          Dummy2 150B     Dummy12 150B    Dummy23 100B
 *         update.txt        Dummy3 100B     Dummy13 100B
 *         test.jpg
 *         invalid.jpg
 */
public class InspectorProvider extends TestRootProvider {

    public static final String AUTHORITY = "com.android.documentsui.inspectorprovider";
    public static final String OPEN_IN_PROVIDER_TEST = "OpenInProviderTest";
    public static final String ROOT_ID = "inspector-root";
    // Number of items under the root path of InspectorProvider.
    public static final String NUMBER_OF_ITEMS = "6";
    // Virtual jpeg files for test metadata loading from provider.
    // TEST_JPEG is a normal jpeg file with legal exif data.
    // INVALID_JPEG is a invalid jpeg file with broken exif data.
    // TEST_JPEG_WIDTH, TEST_JPEG_HEIGHT are exif information for TEST_JPEG.
    public static final String TEST_JPEG = "test.jpg";
    public static final String INVALID_JPEG = "invalid.jpg";
    public static final int TEST_JPEG_WIDTH = 3840;
    public static final int TEST_JPEG_HEIGHT = 2160;

    private static final String ROOT_DOC_ID = "root0";
    private static final int ROOT_FLAGS = 0;

    public InspectorProvider() {
        super("Inspector Root", ROOT_ID, ROOT_FLAGS, ROOT_DOC_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        if (OPEN_IN_PROVIDER_TEST.equals(documentId)) {
            MatrixCursor c = createDocCursor(projection);
            addFile(c, OPEN_IN_PROVIDER_TEST, FLAG_SUPPORTS_SETTINGS);
            return c;
        }

        MatrixCursor c = createDocCursor(projection);
        addFolder(c, documentId);
        return c;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        // prevent the testing from Security Exception comes from DocumentsProvider
        info.exported = true;
        info.grantUriPermissions = true;
        info.writePermission = android.Manifest.permission.MANAGE_DOCUMENTS;
        info.readPermission = android.Manifest.permission.MANAGE_DOCUMENTS;

        super.attachInfo(context, info);
    }

    @Override
    public Cursor queryChildDocuments(String s, String[] projection, String s1)
            throws FileNotFoundException {

        if("Top".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFolder(c, "Middle");
            addFileWithSize(c, "dummy1", 50);
            addFileWithSize(c, "dummy2", 150);
            addFileWithSize(c, "dummy3", 100);
            return c;
        }
        else if("Middle".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFolder(c, "Bottom");
            addFileWithSize(c, "dummy11", 50);
            addFileWithSize(c, "dummy12", 150);
            addFileWithSize(c, "dummy13", 100);
            return c;
        }
        else if("Bottom".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFileWithSize(c, "dummy21", 50);
            addFileWithSize(c, "dummy22", 150);
            addFileWithSize(c, "dummy23", 100);
            return c;
        }
        else {
            MatrixCursor c = createDocCursor(projection);
            // If you add folder or file here, please modify NUMBER_OF_ITEMS above for
            // #testFolderDetails in InspectorUiTest accordingly.
            addFolder(c, "Top");
            addFile(c, OPEN_IN_PROVIDER_TEST, FLAG_SUPPORTS_SETTINGS);
            addFile(c, "test.txt");
            addFile(c, "update.txt");
            addFile(c, TEST_JPEG, FLAG_SUPPORTS_METADATA);
            addFile(c, INVALID_JPEG, FLAG_SUPPORTS_METADATA);
            return c;
        }
    }

    private void addFileWithSize(MatrixCursor c, String id, long size) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, size);
        row.add(Document.COLUMN_MIME_TYPE, "text/plain");
        row.add(Document.COLUMN_FLAGS, 0);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }

    @Override
    public Bundle getDocumentMetadata(String documentId) throws FileNotFoundException {
        if (TEST_JPEG.contentEquals(documentId)) {
            Bundle metaData = new Bundle();
            metaData.putInt(ExifInterface.TAG_IMAGE_WIDTH, TEST_JPEG_WIDTH);
            metaData.putInt(ExifInterface.TAG_IMAGE_LENGTH, TEST_JPEG_HEIGHT);
            return metaData;
        } else if (INVALID_JPEG.contentEquals(documentId)) {
            // Suppose there are some errors occurs.
            // Return null makes DocumentsContract throw a RemoteExcpetion,
            // and rethrow a RemoteException when using ContentResolver.
            return null;
        }

        return super.getDocumentMetadata(documentId);
    }
}
