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
package com.android.traceur;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.provider.Settings;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.internal.content.FileSystemProvider;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Adds an entry for traces in the file picker.
 */
public class StorageProvider extends FileSystemProvider{

    public static final String TAG = StorageProvider.class.getName();
    public static final String AUTHORITY = "com.android.traceur.documents";

    private static final String DOC_ID_ROOT = "traces";
    private static final String ROOT_DIR = "/data/local/traces";
    private static final String MIME_TYPE = "application/vnd.android.systrace";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_DOCUMENT_ID,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
    };

    @Override
    public boolean onCreate() {
        super.onCreate(DEFAULT_DOCUMENT_PROJECTION);
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        boolean developerOptionsIsEnabled =
            Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

        // If developer options is not enabled, return an empty root cursor.
        // This removes the provider from the list entirely.
        if (!developerOptionsIsEnabled) {
            return null;
        }

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, DOC_ID_ROOT);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY);
        row.add(Root.COLUMN_MIME_TYPES, MIME_TYPE);
        row.add(Root.COLUMN_ICON, R.drawable.stat_sys_adb_green);
        row.add(Root.COLUMN_TITLE,
            getContext().getString(R.string.system_traces_storage_title));
        row.add(Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT);
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        final MatrixCursor.RowBuilder row = result.newRow();
        File file;
        String mimeType;

        if (DOC_ID_ROOT.equals(documentId)) {
            file = new File(ROOT_DIR);
            mimeType = Document.MIME_TYPE_DIR;
        } else {
            file = getFileForDocId(documentId);
            mimeType = MIME_TYPE;
        }

        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_PREFERS_LAST_MODIFIED | Document.FLAG_SUPPORTS_DELETE);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        Cursor result = super.queryChildDocuments(parentDocumentId, projection, sortOrder);

        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO,
            getContext().getResources().getString(R.string.system_trace_sensitive_data));
        result.setExtras(bundle);

        return result;
    }


    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException, UnsupportedOperationException {
        if (ParcelFileDescriptor.parseMode(mode) != ParcelFileDescriptor.MODE_READ_ONLY) {
            throw new UnsupportedOperationException(
                "Attempt to open read-only file " + documentId + " in mode " + mode);
        }
        return ParcelFileDescriptor.open(getFileForDocId(documentId),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    @Override
    protected Uri buildNotificationUri(String docId) {
        return DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId);
    }

    @Override
    protected String getDocIdForFile(File file) {
        return DOC_ID_ROOT + ":" + file.getName();
    }

    @Override
    protected File getFileForDocId(String documentId, boolean visible)
            throws FileNotFoundException {
        if (DOC_ID_ROOT.equals(documentId)) {
            return new File(ROOT_DIR);
        } else {
            final int splitIndex = documentId.indexOf(':', 1);
            final String name = documentId.substring(splitIndex + 1);
            if (splitIndex == -1 || !DOC_ID_ROOT.equals(documentId.substring(0, splitIndex)) ||
                    !FileUtils.isValidExtFilename(name)) {
                throw new FileNotFoundException("Invalid document ID: " + documentId);
            }
            final File file = new File(ROOT_DIR, name);
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + documentId);
            }
            return file;
        }
    }

}
