/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@WorkerThread
public final class IoUtils {
    private static final String TAG = Clog.tag(IoUtils.class);

    private IoUtils() { }

    public static @NonNull byte[] readFromFile(@NonNull File file) throws IOException  {
        InputStream inputStream = new FileInputStream(file);
        try {
            return readFromStream(inputStream);
        } finally {
            close(inputStream);
        }
    }

    public static @NonNull byte[] readFromStream(@NonNull InputStream inputStream)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            int num;
            byte[] buf = new byte[16384];
            while ((num = inputStream.read(buf, 0, buf.length)) >= 0) {
                buffer.write(buf, 0, num);
            }
            return buffer.toByteArray();
        } finally {
            close(buffer);
        }
    }

    public static void writeToStream(@NonNull OutputStream outputStream, @NonNull byte[] buffer)
            throws IOException {
        outputStream.write(buffer);
        outputStream.flush();
    }

    public static void close(@Nullable Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            Clog.w(TAG, "Failed to close '" + closeable + "'", e);
        }
    }
}
