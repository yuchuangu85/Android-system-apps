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

import static org.apache.commons.compress.archivers.ArchiveStreamFactory.TAR;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.BROTLI;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.BZIP2;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.GZIP;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.XZ;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.compressors.brotli.BrotliUtils;
import org.apache.commons.compress.compressors.xz.XZUtils;

/**
 * To query how to generate ArchiveHandle, how to create CompressInputStream and how to create
 * ArchiveInputStream by using MIME type in ArchiveRegistry.
 */
final class ArchiveRegistry {
    static final int COMMON_ARCHIVE_TYPE = 1;
    static final int ZIP_TYPE = 2;
    static final int SEVEN_Z_TYPE = 3;

    /**
     * The mapping between MIME type and how to create ArchiveHandle instance.
     * key - MIME type
     * value - the integer value used in ArchiveHandle.create
     */
    private static final Map<String, Integer> sHandleArchiveMap = new HashMap<>();

    /**
     * The mapping between MIME type and the archive name that is used by ArchiveStreamFactory.
     * key - MIME type
     * value - the archive name is used as the 1st parameter of ArchiveStreamFactory
     *                 .createArchiveInputStream
     */
    private static final Map<String, String> sMimeTypeArchiveNameMap = new HashMap<>();

    /**
     * The mapping between MIME type and the compress name that is used by CompressorStreamFactory.
     * key - MIME type
     * value - the compress name is used as the 1st parameter of CompressorStreamFactory
     *                 .createCompressorInputStream
     */
    private static final Map<String, String> sMimeTypeCompressNameMap = new HashMap<>();

    static {
        /* initial sHandleArchiveMap */
        sHandleArchiveMap.put("application/zip", ZIP_TYPE);
        sHandleArchiveMap.put("application/x-zip", ZIP_TYPE);
        sHandleArchiveMap.put("application/x-zip-compressed", ZIP_TYPE);
        sHandleArchiveMap.put("application/x-7z-compressed", SEVEN_Z_TYPE);
        sHandleArchiveMap.put("application/x-gtar", COMMON_ARCHIVE_TYPE);
        sHandleArchiveMap.put("application/x-tar", COMMON_ARCHIVE_TYPE);
        sHandleArchiveMap.put("application/x-compressed-tar", COMMON_ARCHIVE_TYPE);
        sHandleArchiveMap.put("application/x-gtar-compressed", COMMON_ARCHIVE_TYPE);
        sHandleArchiveMap.put("application/x-bzip-compressed-tar", COMMON_ARCHIVE_TYPE);
        if (BrotliUtils.isBrotliCompressionAvailable()) {
            sHandleArchiveMap.put("application/x-brotli-compressed-tar", COMMON_ARCHIVE_TYPE);
        }
        if (XZUtils.isXZCompressionAvailable()) {
            sHandleArchiveMap.put("application/x-xz-compressed-tar", COMMON_ARCHIVE_TYPE);
        }

        /* initial sMimeTypeArchiveNameMap */
        sMimeTypeArchiveNameMap.put("application/x-gtar", TAR);
        sMimeTypeArchiveNameMap.put("application/x-tar", TAR);
        sMimeTypeArchiveNameMap.put("application/x-compressed-tar", TAR);
        sMimeTypeArchiveNameMap.put("application/x-gtar-compressed", TAR);
        sMimeTypeArchiveNameMap.put("application/x-bzip-compressed-tar", TAR);
        sMimeTypeArchiveNameMap.put("application/x-brotli-compressed-tar", TAR);
        sMimeTypeArchiveNameMap.put("application/x-xz-compressed-tar", TAR);

        /* initial sMimeTypeCompressNameMap */
        sMimeTypeCompressNameMap.put("application/x-compressed-tar", GZIP);
        sMimeTypeCompressNameMap.put("application/x-gtar-compressed", GZIP);
        sMimeTypeCompressNameMap.put("application/x-bzip-compressed-tar", BZIP2);
        if (BrotliUtils.isBrotliCompressionAvailable()) {
            sMimeTypeCompressNameMap.put("application/x-brotli-compressed-tar", BROTLI);
        }
        if (XZUtils.isXZCompressionAvailable()) {
            sMimeTypeCompressNameMap.put("application/x-xz-compressed-tar", XZ);
        }
    }

    /**
     * To query the archive name by passing MIME type is used by
     * ArchiveStreamFactory.createArchiveInputStream.
     *
     * @param mimeType the MIME type of the archive file
     * @return the archive name to tell ArchiveStreamFactory how to extract archive
     */
    @Nullable
    static String getArchiveName(String mimeType) {
        return sMimeTypeArchiveNameMap.get(mimeType);
    }

    /**
     * To query the compress name by passing MIME type is used by
     * CompressorStreamFactory.createCompressorInputStream.
     *
     * @param mimeType the MIME type of the compressed file
     * @return the compress name to tell CompressorStreamFactory how to uncompress
     */
    @Nullable
    static String getCompressName(String mimeType) {
        return sMimeTypeCompressNameMap.get(mimeType);
    }

    /**
     * To query the method to uncompress the compressed file by MIME type.
     *
     * @param mimeType the MIME type of the compressed file
     * @return the method describe how to uncompress the compressed file
     */
    @Nullable
    static Integer getArchiveType(String mimeType) {
        return sHandleArchiveMap.get(mimeType);
    }

    static Set<String> getSupportList() {
        return sHandleArchiveMap.keySet();
    }
}
