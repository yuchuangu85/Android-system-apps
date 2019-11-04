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

import static androidx.core.util.Preconditions.checkArgument;
import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.archives.ArchiveRegistry.COMMON_ARCHIVE_TYPE;
import static com.android.documentsui.archives.ArchiveRegistry.SEVEN_Z_TYPE;
import static com.android.documentsui.archives.ArchiveRegistry.ZIP_TYPE;

import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * To handle to all of supported support types of archive or compressed+archive files.
 * @param <T> the archive class such as SevenZFile, ZipFile, ArchiveInputStream etc.
 */
abstract class ArchiveHandle<T> implements Closeable {
    private static final String TAG = ArchiveHandle.class.getSimpleName();
    /**
     * To re-create the CommonArchive that belongs to SevenZFile, ZipFile, or
     * ArchiveInputStream. It needs file descriptor to create the input stream or seek to the head.
     */
    @NonNull
    private final ParcelFileDescriptor mParcelFileDescriptor;

    /**
     * To re-create the CommonArchive that belongs to SevenZFile, ZipFile, or
     * ArchiveInputStream. It needs MIME type to know how to re-create.
     */
    @NonNull
    private final String mMimeType;

    /**
     * CommonArchive is generic type. It may be SevenZFile, ZipFile, or ArchiveInputStream.
     */
    @NonNull
    private T mCommonArchive;

    /**
     * To use factory pattern ensure the only one way to create the ArchiveHandle instance.
     * @param parcelFileDescriptor the file descriptor
     * @param mimeType the mime type of the file
     * @param commonArchive the common archive instance
     */
    private ArchiveHandle(@NonNull ParcelFileDescriptor parcelFileDescriptor,
                          @NonNull String mimeType,
                          @NonNull T commonArchive) {
        mParcelFileDescriptor = parcelFileDescriptor;
        mMimeType = mimeType;
        mCommonArchive = commonArchive;
    }

    /**
     * It's used to re-create the file input stream. Just like SevenZFile or ArchiveInputStream.
     *
     * @return the file input stream
     */
    @NonNull
    private FileInputStream recreateCommonArchiveStream() throws IOException {
        FileInputStream fileInputStream =
                new FileInputStream(mParcelFileDescriptor.getFileDescriptor());
        SeekableByteChannel seekableByteChannel = fileInputStream.getChannel();
        seekableByteChannel.position(0);
        return fileInputStream;
    }

    /**
     * To get the MIME type of the file.
     * @return the MIME type of file
     */
    @NonNull
    protected String getMimeType() {
        return mMimeType;
    }

    /**
     * To get the common archive instance.
     *
     * @return the common archive instance.
     */
    @NonNull
    public final T getCommonArchive() {
        return mCommonArchive;
    }

    private void setCommonArchive(@NonNull T commonArchive) {
        mCommonArchive = commonArchive;
    }

    /**
     * Neither SevenZFile nor ArchiveInputStream likes ZipFile that has the API
     * getInputStream(ArchiveEntry), rewind or reset, so it needs to close the
     * current instance and recreate a new one.
     *
     * @param archiveEntry the target entry
     * @return the input stream related to archiveEntry
     * @throws IOException invalid file descriptor may raise the IOException
     * @throws CompressorException invalid compress name may raise the CompressException
     * @throws ArchiveException invalid Archive name may raise the ArchiveException
     */
    protected InputStream getInputStream(@NonNull ArchiveEntry archiveEntry)
            throws IOException, CompressorException, ArchiveException {

        if (!isCommonArchiveSupportGetInputStream()) {
            FileInputStream fileInputStream = recreateCommonArchiveStream();
            T commonArchive = recreateCommonArchive(fileInputStream);
            if (commonArchive != null) {
                closeCommonArchive();
                setCommonArchive(commonArchive);
            } else {
                Log.e(TAG, "new SevenZFile or ArchiveInputStream is null");
                fileInputStream.close();
            }
        }

        return ArchiveEntryInputStream.create(this, archiveEntry);
    }

    boolean isCommonArchiveSupportGetInputStream() {
        return false;
    }

    void closeCommonArchive() throws IOException {
        throw new UnsupportedOperationException("This kind of ArchiveHandle doesn't support");
    }

    T recreateCommonArchive(FileInputStream fileInputStream)
            throws CompressorException, ArchiveException, IOException {
        throw new UnsupportedOperationException("This kind of ArchiveHandle doesn't support");
    }

    public void close() throws IOException {
        mParcelFileDescriptor.close();
    }

    /**
     * To get the enumeration of all of entries from archive.
     * @return the enumeration of all of entries from archive
     * @throws IOException it may raise the IOException when the archiveHandle get the next entry
     */
    @NonNull
    public abstract Enumeration<? extends ArchiveEntry> getEntries() throws IOException;

    private static class SevenZFileHandle extends ArchiveHandle<SevenZFile> {
        SevenZFileHandle(ParcelFileDescriptor parcelFileDescriptor, String mimeType,
                         SevenZFile commonArchive) {
            super(parcelFileDescriptor, mimeType, commonArchive);
        }

        @Override
        protected void closeCommonArchive() throws IOException {
            getCommonArchive().close();
        }

        @Override
        protected SevenZFile recreateCommonArchive(@NonNull FileInputStream fileInputStream)
                throws IOException {
            return new SevenZFile(fileInputStream.getChannel());
        }

        @NonNull
        @Override
        public Enumeration<? extends ArchiveEntry> getEntries() {
            if (getCommonArchive().getEntries() == null) {
                return Collections.emptyEnumeration();
            }

            return Collections.enumeration(
                    (Collection<? extends ArchiveEntry>) getCommonArchive().getEntries());
        }
    }

    private static class ZipFileHandle extends ArchiveHandle<ZipFile> {
        ZipFileHandle(ParcelFileDescriptor parcelFileDescriptor, String mimeType,
                      ZipFile commonArchive) {
            super(parcelFileDescriptor, mimeType, commonArchive);
        }

        @Override
        protected boolean isCommonArchiveSupportGetInputStream() {
            return true;
        }

        @NonNull
        @Override
        public Enumeration<? extends ArchiveEntry> getEntries() {
            final Enumeration<ZipArchiveEntry> enumeration = getCommonArchive().getEntries();
            if (enumeration == null) {
                return Collections.emptyEnumeration();
            }
            return enumeration;
        }
    }

    private static class CommonArchiveInputHandle extends ArchiveHandle<ArchiveInputStream> {
        CommonArchiveInputHandle(ParcelFileDescriptor parcelFileDescriptor,
                                 String mimeType, ArchiveInputStream commonArchive) {
            super(parcelFileDescriptor, mimeType, commonArchive);
        }

        @Override
        protected void closeCommonArchive() throws IOException {
            getCommonArchive().close();
        }

        @Override
        protected ArchiveInputStream recreateCommonArchive(FileInputStream fileInputStream)
                throws CompressorException, ArchiveException {
            return createCommonArchive(fileInputStream, getMimeType());
        }

        @NonNull
        @Override
        public Enumeration<? extends ArchiveEntry> getEntries() throws IOException {
            final ArchiveInputStream archiveInputStream = getCommonArchive();
            final List<ArchiveEntry> list = new ArrayList<>();
            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                list.add(entry);
            }

            return Collections.enumeration(list);
        }
    }

    @NonNull
    private static ArchiveInputStream createCommonArchive(
            @NonNull FileInputStream fileInputStream,
            @NonNull String mimeType) throws CompressorException, ArchiveException {
        InputStream inputStream = fileInputStream;

        String compressName = ArchiveRegistry.getCompressName(mimeType);
        if (!TextUtils.isEmpty(compressName)) {
            CompressorStreamFactory compressorStreamFactory =
                    new CompressorStreamFactory();
            inputStream = compressorStreamFactory
                    .createCompressorInputStream(compressName, inputStream);
        }

        ArchiveStreamFactory archiveStreamFactory = new ArchiveStreamFactory();
        String archiveName = ArchiveRegistry.getArchiveName(mimeType);
        if (TextUtils.isEmpty(archiveName)) {
            throw new ArchiveException("Invalid archive name.");
        }

        return archiveStreamFactory
                .createArchiveInputStream(archiveName, inputStream);
    }

    /**
     * The only one way creates the instance of ArchiveHandle.
     */
    public static ArchiveHandle create(@NonNull ParcelFileDescriptor parcelFileDescriptor,
            @NonNull String mimeType) throws CompressorException, ArchiveException, IOException {
        checkNotNull(parcelFileDescriptor);
        checkArgument(!TextUtils.isEmpty(mimeType));

        Integer archiveType = ArchiveRegistry.getArchiveType(mimeType);
        if (archiveType == null) {
            throw new UnsupportedOperationException("Doesn't support MIME type " + mimeType);
        }

        FileInputStream fileInputStream =
                new FileInputStream(parcelFileDescriptor.getFileDescriptor());

        switch (archiveType) {
            case COMMON_ARCHIVE_TYPE:
                ArchiveInputStream archiveInputStream =
                        createCommonArchive(fileInputStream, mimeType);
                return new CommonArchiveInputHandle(parcelFileDescriptor, mimeType,
                        archiveInputStream);
            case ZIP_TYPE:
                SeekableByteChannel zipFileChannel = fileInputStream.getChannel();
                try {
                    ZipFile zipFile = new ZipFile(zipFileChannel);
                    return new ZipFileHandle(parcelFileDescriptor, mimeType,
                            zipFile);
                } catch (Exception e) {
                    FileUtils.closeQuietly(zipFileChannel);
                    throw e;
                }
            case SEVEN_Z_TYPE:
                SeekableByteChannel sevenZFileChannel = fileInputStream.getChannel();
                try {
                    SevenZFile sevenZFile = new SevenZFile(sevenZFileChannel);
                    return new SevenZFileHandle(parcelFileDescriptor, mimeType,
                            sevenZFile);
                } catch (Exception e) {
                    FileUtils.closeQuietly(sevenZFileChannel);
                    throw e;
                }
            default:
                throw new UnsupportedOperationException("Doesn't support MIME type "
                        + mimeType);
        }
    }
}
