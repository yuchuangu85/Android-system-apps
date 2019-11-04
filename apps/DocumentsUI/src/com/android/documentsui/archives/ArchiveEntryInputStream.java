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

package com.android.documentsui.archives;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * To simulate the input stream by using ZipFile, SevenZFile, or ArchiveInputStream.
 */
abstract class ArchiveEntryInputStream extends InputStream {
    private final long mSize;
    private final ReadSource mReadSource;

    private ArchiveEntryInputStream(ReadSource readSource, @NonNull ArchiveEntry archiveEntry) {
        mReadSource = readSource;
        mSize = archiveEntry.getSize();
    }

    @Override
    public int read() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (mReadSource != null) {
            return mReadSource.read(b, off, len);
        }

        return -1; /* end of input stream */
    }

    @Override
    public int available() throws IOException {
        return mReadSource == null ? 0 : StrictMath.toIntExact(mSize);
    }

    /**
     * The interface describe how to make the ArchiveHandle to next entry.
     */
    private interface NextEntryIterator {
        ArchiveEntry getNextEntry() throws IOException;
    }

    /**
     * The interface provide where to read the data.
     */
    private interface ReadSource {
        int read(byte[] b, int off, int len) throws IOException;
    }

    private static boolean moveToArchiveEntry(
            NextEntryIterator nextEntryIterator, ArchiveEntry archiveEntry) throws IOException {
        ArchiveEntry entry;
        while ((entry = nextEntryIterator.getNextEntry()) != null) {
            if (TextUtils.equals(entry.getName(), archiveEntry.getName())) {
                return true;
            }
        }
        return false;
    }

    private static class WrapArchiveInputStream extends ArchiveEntryInputStream {
        private WrapArchiveInputStream(ReadSource readSource,
                ArchiveEntry archiveEntry, NextEntryIterator iterator) throws IOException {
            super(readSource, archiveEntry);

            moveToArchiveEntry(iterator, archiveEntry);
        }
    }

    private static class WrapZipFileInputStream extends ArchiveEntryInputStream {
        private final Closeable mCloseable;

        private WrapZipFileInputStream(
                ReadSource readSource, @NonNull ArchiveEntry archiveEntry, Closeable closeable)
                throws IOException {
            super(readSource, archiveEntry);
            mCloseable = closeable;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (mCloseable != null) {
                mCloseable.close();
            }
        }
    }

    static InputStream create(@NonNull ArchiveHandle archiveHandle,
            @NonNull ArchiveEntry archiveEntry) throws IOException {
        if (archiveHandle == null) {
            throw new IllegalArgumentException("archiveHandle is null");
        }

        if (archiveEntry == null) {
            throw new IllegalArgumentException("ArchiveEntry is empty");
        }

        if (archiveEntry.isDirectory() || archiveEntry.getSize() <= 0
                || TextUtils.isEmpty(archiveEntry.getName())) {
            throw new IllegalArgumentException("ArchiveEntry is an invalid file entry");
        }

        Object commonArchive = archiveHandle.getCommonArchive();

        if (commonArchive instanceof SevenZFile) {
            return new WrapArchiveInputStream(
                (b, off, len) -> ((SevenZFile) commonArchive).read(b, off, len),
                archiveEntry,
                () -> ((SevenZFile) commonArchive).getNextEntry());
        } else if (commonArchive instanceof ZipFile) {
            final InputStream inputStream =
                    ((ZipFile) commonArchive).getInputStream((ZipArchiveEntry) archiveEntry);
            return new WrapZipFileInputStream(
                (b, off, len) -> inputStream.read(b, off, len),
                archiveEntry,
                () -> inputStream.close());
        } else if (commonArchive instanceof ArchiveInputStream) {
            return new WrapArchiveInputStream(
                (b, off, len) -> ((ArchiveInputStream) commonArchive).read(b, off, len),
                archiveEntry,
                () -> ((ArchiveInputStream) commonArchive).getNextEntry());
        }

        return null;
    }
}
