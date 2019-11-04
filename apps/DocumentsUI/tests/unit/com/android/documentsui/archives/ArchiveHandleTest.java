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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.runner.AndroidJUnit4;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ArchiveHandleTest {
    @Rule
    public ArchiveFileTestRule mArchiveFileTestRule = new ArchiveFileTestRule();


    private ArchiveHandle prepareArchiveHandle(String archivePath, String suffix,
            String mimeType) throws IOException, CompressorException, ArchiveException {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile(archivePath, suffix);

        return ArchiveHandle.create(parcelFileDescriptor, mimeType);
    }

    private static ArchiveEntry getFileInArchive(Enumeration<ArchiveEntry> enumeration,
            String pathInArchive) {
        while (enumeration.hasMoreElements()) {
            ArchiveEntry entry = enumeration.nextElement();
            if (entry.getName().equals(pathInArchive)) {
                return entry;
            }
        }
        return null;
    }


    private static class ArchiveEntryRecord implements ArchiveEntry {
        private final String mName;
        private final long mSize;
        private final boolean mIsDirectory;

        private ArchiveEntryRecord(ArchiveEntry archiveEntry) {
            this(archiveEntry.getName(), archiveEntry.getSize(), archiveEntry.isDirectory());
        }

        private ArchiveEntryRecord(String name, long size, boolean isDirectory) {
            mName = name;
            mSize = size;
            mIsDirectory = isDirectory;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == null) {
                return false;
            }

            if (obj instanceof ArchiveEntryRecord) {
                ArchiveEntryRecord recordB = (ArchiveEntryRecord) obj;
                return mName.equals(recordB.mName)
                        && mSize == recordB.mSize
                        && mIsDirectory == recordB.mIsDirectory;
            }

            return false;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public long getSize() {
            return mSize;
        }

        @Override
        public boolean isDirectory() {
            return mIsDirectory;
        }

        @Override
        public Date getLastModifiedDate() {
            return null;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "name: %s, size: %d, isDirectory: %b",
                    mName, mSize, mIsDirectory);
        }
    }

    private static List<ArchiveEntry> transformToIterable(Enumeration<ArchiveEntry> enumeration) {
        List list = new ArrayList<ArchiveEntry>();
        while (enumeration.hasMoreElements()) {
            list.add(new ArchiveEntryRecord(enumeration.nextElement()));
        }
        return list;
    }

    private static final List<ArchiveEntryRecord> sExpectEntries =
            new ArrayList<ArchiveEntryRecord>() {
        {
            add(new ArchiveEntryRecord("hello/hello.txt", 48, false));
            add(new ArchiveEntryRecord("hello/inside_folder/hello_insside.txt",
                            14, false));
            add(new ArchiveEntryRecord("hello/hello2.txt", 48, false));
        }
    };


    @Test
    public void buildArchiveHandle_withoutFileDescriptor_shouldBeIllegal() throws Exception {
        try {
            ArchiveHandle.create(null,
                    "application/x-7z-compressed");
            fail("It should not be here!");
        } catch (NullPointerException e) {
            /* do nothing */
        }
    }

    @Test
    public void buildArchiveHandle_withWrongMimeType_shouldBeIllegal() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/7z/hello.7z", ".7z");

        try {
            ArchiveHandle.create(parcelFileDescriptor, null);
            fail("It should not be here!");
        } catch (IllegalArgumentException e) {
            /* do nothing */
        }
    }

    @Test
    public void buildArchiveHandle_sevenZFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z",
                ".7z", "application/x-7z-compressed");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_zipFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_zipWithWrongMimeType_shouldBeNull() throws Exception {
        try {
            prepareArchiveHandle("archives/zip/hello.zip",
                    ".zip", "application/xxxzip");
            fail("It should not be here!");
        } catch (UnsupportedOperationException e) {
            /* do nothing */
        }
    }

    @Test
    public void buildArchiveHandle_tarFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar/hello.tar",
                ".tar","application/x-gtar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_tgzFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_gz/hello.tgz",
                ".tgz", "application/x-compressed-tar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_tarGzFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar_gz/hello_tar_gz", ".tar.gz",
                        "application/x-compressed-tar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_tarBzipFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar_bz2/hello.tar.bz2",
                        ".tar.bz2", "application/x-bzip-compressed-tar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_tarXzFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/xz/hello.tar.xz", ".tar.xz",
                        "application/x-xz-compressed-tar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void buildArchiveHandle_tarBrFile_shouldNotNull() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/brotli/hello.tar.br", ".tar.br",
                "application/x-brotli-compressed-tar");

        assertThat(archiveHandle).isNotNull();
    }

    @Test
    public void getMimeType_sevenZFile_shouldBeSevenZ()
            throws CompressorException, ArchiveException, IOException {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z",
                ".7z", "application/x-7z-compressed");

        assertThat(archiveHandle.getMimeType()).isEqualTo("application/x-7z-compressed");
    }

    @Test
    public void getMimeType_tarBrotli_shouldBeBrotliCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/brotli/hello.tar.br", ".tar.br",
                        "application/x-brotli-compressed-tar");

        assertThat(archiveHandle.getMimeType())
                .isEqualTo("application/x-brotli-compressed-tar");
    }

    @Test
    public void getMimeType_tarXz_shouldBeXzCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/xz/hello.tar.xz", ".tar.xz",
                        "application/x-xz-compressed-tar");

        assertThat(archiveHandle.getMimeType())
                .isEqualTo("application/x-xz-compressed-tar");
    }

    @Test
    public void getMimeType_tarGz_shouldBeCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar_gz/hello_tar_gz", ".tar.gz",
                        "application/x-compressed-tar");

        assertThat(archiveHandle.getMimeType())
                .isEqualTo("application/x-compressed-tar");
    }

    @Test
    public void getCommonArchive_tarBrFile_shouldBeCommonArchiveInputHandle() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/brotli/hello.tar.br", ".tar.br",
                        "application/x-brotli-compressed-tar");

        assertThat(archiveHandle.toString()).contains("CommonArchiveInputHandle");
    }

    @Test
    public void getCommonArchive_sevenZFile_shouldBeSevenZFileHandle() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z",
                ".7z", "application/x-7z-compressed");

        assertThat(archiveHandle.toString()).contains("SevenZFileHandle");
    }


    @Test
    public void getCommonArchive_zipFile_shouldBeZipFileHandle() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        assertThat(archiveHandle.toString()).contains("ZipFileHandle");
    }

    @Test
    public void close_zipFile_shouldBeSuccess() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        archiveHandle.close();
    }

    @Test
    public void close_sevenZFile_shouldBeSuccess() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z",
                ".7z", "application/x-7z-compressed");

        archiveHandle.close();
    }

    @Test
    public void closeInputStream_zipFile_shouldBeSuccess() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        InputStream inputStream = archiveHandle.getInputStream(
                getFileInArchive(archiveHandle.getEntries(),
                        "hello/inside_folder/hello_insside.txt"));

        assertThat(inputStream).isNotNull();

        inputStream.close();
    }

    @Test
    public void close_zipFile_shouldNotOpen() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor =  mArchiveFileTestRule
                .openAssetFile("archives/zip/hello.zip", ".zip");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/zip");

        archiveHandle.close();

        FileInputStream fileInputStream =
                new FileInputStream(parcelFileDescriptor.getFileDescriptor());
        assertThat(fileInputStream).isNotNull();
    }

    @Test
    public void getInputStream_zipFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/zip/hello.zip", ".zip");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/zip");

        InputStream inputStream = archiveHandle.getInputStream(
                getFileInArchive(archiveHandle.getEntries(),
                        "hello/inside_folder/hello_insside.txt"));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream))
                .isEqualTo(expectedContent);
    }

    @Test
    public void getInputStream_zipFileNotExistEntry_shouldFail() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.getName()).thenReturn("/not_exist_entry");

        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not be here.");
        } catch (IllegalArgumentException | ArchiveException | CompressorException e) {
            /* do nothing */
        }
    }

    @Test
    public void getInputStream_directoryEntry_shouldFail() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.isDirectory()).thenReturn(true);

        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not be here.");
        } catch (IllegalArgumentException e) {
            /* expected, do nothing */
        }
    }

    @Test
    public void getInputStream_zeroSizeEntry_shouldFail() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.isDirectory()).thenReturn(false);
        when(archiveEntry.getSize()).thenReturn(0L);

        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not be here.");
        } catch (IllegalArgumentException e) {
            /* expected, do nothing */
        }
    }

    @Test
    public void getInputStream_emptyStringEntry_shouldFail() throws Exception {
        ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip",
                ".zip", "application/zip");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.isDirectory()).thenReturn(false);
        when(archiveEntry.getSize()).thenReturn(14L);
        when(archiveEntry.getName()).thenReturn("");

        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not be here.");
        } catch (IllegalArgumentException e) {
            /* expected, do nothing */
        }
    }

    @Test
    public void getInputStream_sevenZFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/7z/hello.7z", ".7z");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-7z-compressed");

        InputStream inputStream = archiveHandle.getInputStream(
                getFileInArchive(archiveHandle.getEntries(),
                        "hello/inside_folder/hello_insside.txt"));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream))
                .isEqualTo(expectedContent);
    }

    @Test
    public void getInputStream_tarGzFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/tar_gz/hello.tgz", ".tar.gz");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-compressed-tar");

        InputStream inputStream = archiveHandle.getInputStream(
                getFileInArchive(archiveHandle.getEntries(),
                        "hello/inside_folder/hello_insside.txt"));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream))
                .isEqualTo(expectedContent);
    }

    @Test
    public void getInputStream_tarGzFileNullEntry_getNullInputStream() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/tar_gz/hello.tgz", ".tar.gz");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-compressed-tar");

        try {
            archiveHandle.getInputStream(null);
            fail("It should not here");
        } catch (IllegalArgumentException | ArchiveException | CompressorException e) {
            /* expected, do nothing */
        }
    }


    @Test
    public void getInputStream_tarGzFileInvalidEntry_getNullInputStream() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/tar_gz/hello.tgz", ".tar.gz");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-compressed-tar");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.getName()).thenReturn("");
        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not here");
        } catch (IllegalArgumentException | ArchiveException | CompressorException e) {
            /* expected, do nothing */
        }
    }

    @Test
    public void getInputStream_tarBrotliFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule
                .openAssetFile("archives/brotli/hello.tar.br", ".tar.br");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-brotli-compressed-tar");

        InputStream inputStream = archiveHandle.getInputStream(
                getFileInArchive(archiveHandle.getEntries(),
                        "hello/inside_folder/hello_insside.txt"));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream))
                .isEqualTo(expectedContent);
    }

    @Test
    public void getEntries_zipFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                        "application/zip");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }

    @Test
    public void getEntries_tarFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar/hello.tar", ".tar",
                "application/x-gtar");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }

    @Test
    public void getEntries_tgzFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar_gz/hello.tgz", ".tgz",
                        "application/x-compressed-tar");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }

    @Test
    public void getEntries_tarBzFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/tar_bz2/hello.tar.bz2", ".tar.bz2",
                        "application/x-bzip-compressed-tar");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }

    @Test
    public void getEntries_tarBrotliFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/brotli/hello.tar.br", ".tar.br",
                        "application/x-brotli-compressed-tar");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }

    @Test
    public void getEntries_tarXzFile_shouldTheSameWithList() throws Exception {
        ArchiveHandle archiveHandle =
                prepareArchiveHandle("archives/xz/hello.tar.xz", ".tar.xz",
                        "application/x-xz-compressed-tar");

        assertThat(transformToIterable(archiveHandle.getEntries()))
                .containsAllIn(sExpectEntries);
    }
}