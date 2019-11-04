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

import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.rules.TestName;
import org.junit.runner.Description;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.test.platform.app.InstrumentationRegistry;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

public class ArchiveFileTestRule extends TestName {
    private static final String TAG = ArchiveFileTestRule.class.getSimpleName();

    private final List<Path> mTemporaries;
    private final List<ParcelFileDescriptor> mParcelFileDescriptors;

    private String mClassName;
    private String mMethodName;
    private Path mTemporaryPath;

    public ArchiveFileTestRule() {
        mTemporaries = new ArrayList<>();
        mParcelFileDescriptors = new ArrayList<>();
    }

    @Override
    protected void starting(Description description) {
        super.starting(description);
        mClassName = description.getClassName();
        mMethodName = description.getMethodName();

        try {
            mTemporaryPath = Files.createTempDirectory(
                    InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getCacheDir().toPath(),
                    mClassName);
        } catch (IOException e) {
            Log.e(TAG, String.format(Locale.ENGLISH,
                    "It can't create temporary directory in the staring of %s.%s.",
                    mClassName, mMethodName));
        }
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);

        for (Path path : mTemporaries) {
            if (path != null) {
                path.toFile().delete();
            }
        }

        mTemporaryPath.toFile().delete();

        for (ParcelFileDescriptor parcelFileDescriptor : mParcelFileDescriptors) {
            IOUtils.closeQuietly(parcelFileDescriptor);
        }
    }

    /**
     * To generate the temporary file and return the file path.
     *
     * @param suffix the suffix of the temporary file name
     * @return the file path
     * @throws IOException to create temporary file fail raises IOException
     */
    public Path generateFile(String suffix) throws IOException {
        Set<PosixFilePermission> perm = PosixFilePermissions.fromString("rwx------");
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perm);
        Path filePath = Files.createTempFile(mTemporaryPath, mMethodName, suffix, attr);
        mTemporaries.add(filePath);
        return filePath;
    }

    /**
     * To dump asset path file as temporary file. There are some problems to get the file
     * descriptor from the asset files in instrumentation context. It's null pointer. It needs to
     * dump to temporary file in the target context of the instrumentation.
     *
     * @param assetPath assetPath in test context
     * @param suffix the suffix of the temporary file name
     * @return the file path
     */
    public Path dumpAssetFile(String assetPath, String suffix) throws IOException {
        Path destinationPath = generateFile(suffix);

        try (InputStream inputStream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(assetPath)) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return destinationPath;
    }

    /**
     * To dump asset path file as temporary file. There are some problems to get the file
     * descriptor from the asset files in instrumentation context. It's null pointer. It needs to
     * dump to temporary file in the target context of the instrumentation.
     *
     * @param assetPath assetPath in test context
     * @param suffix the suffix of the temporary file name
     * @return the file path
     */
    public ParcelFileDescriptor openAssetFile(String assetPath, String suffix) throws IOException {
        Path destinationPath = dumpAssetFile(assetPath, suffix);

        ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor
                .open(destinationPath.toFile(), MODE_READ_ONLY);

        mParcelFileDescriptors.add(parcelFileDescriptor);
        return parcelFileDescriptor;
    }

    /**
     * To get asset content that is a type of text.
     *
     * @param assetPath assetPath in test context
     * @return the text content
     */
    public String getAssetText(String assetPath) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = openAssetFile(assetPath, ".text");

        try (FileInputStream fileInputStream =
                     new FileInputStream(parcelFileDescriptor.getFileDescriptor())){
            return getStringFromInputStream(fileInputStream);
        }
    }

    public static String getStringFromInputStream(InputStream inputStream) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

        int size = inputStream.available();
        char[] buffer = new char[size];

        inputStreamReader.read(buffer);

        return new String(buffer);
    }
}
