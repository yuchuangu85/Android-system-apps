/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.archives;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;

import androidx.annotation.IdRes;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.tests.R;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ReadableArchiveTest {
    private static final Uri ARCHIVE_URI = Uri.parse("content://i/love/strawberries");
    private static final String NOTIFICATION_URI =
            "content://com.android.documentsui.archives/notification-uri";
    private ExecutorService mExecutor = null;
    private Archive mArchive = null;
    private TestUtils mTestUtils = null;

    @Before
    public void setUp() throws Exception {
        mExecutor = Executors.newSingleThreadExecutor();
        mTestUtils = new TestUtils(InstrumentationRegistry.getTargetContext(),
                InstrumentationRegistry.getContext(), mExecutor);
    }

    @After
    public void tearDown() throws Exception {
        mExecutor.shutdown();
        assertTrue(mExecutor.awaitTermination(3 /* timeout */, TimeUnit.SECONDS));
        if (mArchive != null) {
            mArchive.close();
        }
    }

    public static ArchiveId createArchiveId(String path) {
        return new ArchiveId(ARCHIVE_URI, ParcelFileDescriptor.MODE_READ_ONLY, path);
    }

    private void loadArchive(ParcelFileDescriptor descriptor, String mimeType)
            throws IOException, CompressorException, ArchiveException {
        mArchive = ReadableArchive.createForParcelFileDescriptor(
                InstrumentationRegistry.getTargetContext(),
                descriptor,
                ARCHIVE_URI,
                mimeType,
                ParcelFileDescriptor.MODE_READ_ONLY,
                Uri.parse(NOTIFICATION_URI));
    }

    private void loadArchive(ParcelFileDescriptor descriptor)
            throws IOException, CompressorException, ArchiveException {
        loadArchive(descriptor, "application/zip");
    }

    private static void assertRowExist(Cursor cursor, String targetDocId) {
        assertTrue(cursor.moveToFirst());

        boolean found = false;
        final int count = cursor.getCount();
        for (int i = 0; i < count; i++) {
            cursor.moveToPosition(i);
            if (TextUtils.equals(targetDocId, cursor.getString(
                    cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))) {
                found = true;
                break;
            }
        }

        assertTrue(targetDocId + " should be exists", found);
    }

    @Test
    public void testQueryChildDocument()
            throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        final Cursor cursor = mArchive.queryChildDocuments(
                createArchiveId("/").toDocumentId(), null, null);

        assertRowExist(cursor, createArchiveId("/file1.txt").toDocumentId());
        assertEquals("file1.txt",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(13,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertRowExist(cursor, createArchiveId("/dir1/").toDocumentId());
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertRowExist(cursor, createArchiveId("/dir2/").toDocumentId());
        assertEquals("dir2",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));


        // Check if querying children works too.
        final Cursor childCursor = mArchive.queryChildDocuments(
                createArchiveId("/dir1/").toDocumentId(), null, null);

        assertTrue(childCursor.moveToFirst());
        assertEquals(
                createArchiveId("/dir1/cherries.txt").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("cherries.txt",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(17,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
    }

    @Test
    public void testQueryChildDocument_NoDirs()
            throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.no_dirs));
        final Cursor cursor = mArchive.queryChildDocuments(
            createArchiveId("/").toDocumentId(), null, null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                createArchiveId("/dir1/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(cursor.moveToNext());

        final Cursor childCursor = mArchive.queryChildDocuments(
                createArchiveId("/dir1/").toDocumentId(), null, null);

        assertTrue(childCursor.moveToFirst());
        assertEquals(
                createArchiveId("/dir1/dir2/").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir2",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(childCursor.moveToNext());

        final Cursor childCursor2 = mArchive.queryChildDocuments(
                createArchiveId("/dir1/dir2/").toDocumentId(),
                null, null);

        assertTrue(childCursor2.moveToFirst());
        assertEquals(
                createArchiveId("/dir1/dir2/cherries.txt").toDocumentId(),
                childCursor2.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertFalse(childCursor2.moveToNext());
    }

    @Test
    public void testQueryChildDocument_EmptyDirs()
            throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.empty_dirs));
        final Cursor cursor = mArchive.queryChildDocuments(
                createArchiveId("/").toDocumentId(), null, null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                createArchiveId("/dir1/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(cursor.moveToNext());

        final Cursor childCursor = mArchive.queryChildDocuments(
                createArchiveId("/dir1/").toDocumentId(), null, null);

        assertRowExist(childCursor, createArchiveId("/dir1/dir2/").toDocumentId());
        assertEquals("dir2",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertRowExist(childCursor, createArchiveId("/dir1/dir3/").toDocumentId());
        assertEquals(
                createArchiveId("/dir1/dir3/").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir3",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        final Cursor childCursor2 = mArchive.queryChildDocuments(
                createArchiveId("/dir1/dir2/").toDocumentId(),
                null, null);
        assertFalse(childCursor2.moveToFirst());

        final Cursor childCursor3 = mArchive.queryChildDocuments(
                createArchiveId("/dir1/dir3/").toDocumentId(),
                null, null);
        assertFalse(childCursor3.moveToFirst());
    }

    @Test
    public void testGetDocumentType() throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        assertEquals(Document.MIME_TYPE_DIR, mArchive.getDocumentType(
                createArchiveId("/dir1/").toDocumentId()));
        assertEquals("text/plain", mArchive.getDocumentType(
                createArchiveId("/file1.txt").toDocumentId()));
    }

    @Test
    public void testIsChildDocument() throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        final String documentId = createArchiveId("/").toDocumentId();
        assertTrue(mArchive.isChildDocument(documentId,
                createArchiveId("/dir1/").toDocumentId()));
        assertFalse(mArchive.isChildDocument(documentId,
                createArchiveId("/this-does-not-exist").toDocumentId()));
        assertTrue(mArchive.isChildDocument(
                createArchiveId("/dir1/").toDocumentId(),
                createArchiveId("/dir1/cherries.txt").toDocumentId()));
        assertTrue(mArchive.isChildDocument(documentId,
                createArchiveId("/dir1/cherries.txt").toDocumentId()));
    }

    @Test
    public void testQueryDocument() throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        final Cursor cursor = mArchive.queryDocument(
                createArchiveId("/dir2/strawberries.txt").toDocumentId(),
                null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                createArchiveId("/dir2/strawberries.txt").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("strawberries.txt",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(21,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
    }

    private void queryDocumentByResIdWithMimeTypeAndVerify(@IdRes int resId, String mimeType)
            throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getSeekableDescriptor(resId),
                mimeType);
        final String documentId = createArchiveId("/hello/hello.txt").toDocumentId();

        final Cursor cursor = mArchive.queryDocument(documentId, null);
        cursor.moveToNext();

        assertThat(cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
                .isEqualTo(documentId);
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)))
                .isEqualTo("hello.txt");
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
                .isEqualTo("text/plain");
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)))
                .isEqualTo(48);
    }

    @Test
    public void archive_sevenZFile_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_7z,
                "application/x-7z-compressed");
    }

    @Test
    public void archive_tar_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_tar, "application/x-tar");
    }

    @Test
    public void archive_tgz_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_tgz,
                "application/x-compressed-tar");
    }

    @Test
    public void archive_tarXz_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_tar_xz,
                "application/x-xz-compressed-tar");
    }

    @Test
    public void archive_tarBz_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_tar_bz2,
                "application/x-bzip-compressed-tar");
    }

    @Test
    public void archive_tarBrotli_containsList()
            throws IOException, CompressorException, ArchiveException {
        queryDocumentByResIdWithMimeTypeAndVerify(R.raw.hello_tar_br,
                "application/x-brotli-compressed-tar");
    }

    @Test
    public void testOpenDocument()
            throws IOException, CompressorException, ArchiveException, ErrnoException {
        loadArchive(mTestUtils.getSeekableDescriptor(R.raw.archive));
        commonTestOpenDocument();
    }

    @Test
    public void testOpenDocument_NonSeekable()
            throws IOException, CompressorException, ArchiveException, ErrnoException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        commonTestOpenDocument();
    }

    // Common part of testOpenDocument and testOpenDocument_NonSeekable.
    void commonTestOpenDocument() throws IOException, ErrnoException {
        final ParcelFileDescriptor descriptor = mArchive.openDocument(
                createArchiveId("/dir2/strawberries.txt").toDocumentId(),
                "r", null /* signal */);
        assertTrue(Archive.canSeek(descriptor));
        try (final ParcelFileDescriptor.AutoCloseInputStream inputStream =
                new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            Os.lseek(descriptor.getFileDescriptor(), "I love ".length(), OsConstants.SEEK_SET);
            assertEquals("strawberries!", new Scanner(inputStream).nextLine());
            Os.lseek(descriptor.getFileDescriptor(), 0, OsConstants.SEEK_SET);
            assertEquals("I love strawberries!", new Scanner(inputStream).nextLine());
        }
    }

    @Test
    public void testCanSeek() throws IOException {
        assertTrue(Archive.canSeek(mTestUtils.getSeekableDescriptor(R.raw.archive)));
        assertFalse(Archive.canSeek(mTestUtils.getNonSeekableDescriptor(R.raw.archive)));
    }

    @Test
    public void testBrokenArchive() throws IOException, CompressorException, ArchiveException {
        loadArchive(mTestUtils.getNonSeekableDescriptor(R.raw.archive));
        final Cursor cursor = mArchive.queryChildDocuments(
                createArchiveId("/").toDocumentId(), null, null);
    }
}
