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

import android.Manifest;
import android.net.TrafficStats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@WorkerThread
public final class Http {
    private static final String TAG = Clog.tag(Http.class);

    private static final int TRAFFIC_STATS_TAG = 4711; // TODO Assign a better value
    private static final byte[] EMPTY_DATA = new byte[0];

    private Http() { }

    @RequiresPermission(Manifest.permission.INTERNET)
    public static @NonNull byte[] post(@NonNull String uri) throws IOException {
        return post(uri, Headers.NONE, EMPTY_DATA);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    public static @NonNull byte[] post(@NonNull String uri, @NonNull Headers headers)
            throws IOException {
        return post(uri, headers, EMPTY_DATA);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    public static @NonNull byte[] post(@NonNull String uri, @NonNull byte[] data)
            throws IOException {
        return post(uri, Headers.NONE, data);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    public @NonNull static byte[] post(@NonNull String uri, @NonNull Headers headers,
            @NonNull byte[] data) throws IOException {
        return getOrPost(uri, headers, data);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    public static @NonNull byte[] get(@NonNull String uri) throws IOException {
        return get(uri, Headers.NONE);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    public static @NonNull byte[] get(@NonNull String uri, @NonNull Headers headers)
            throws IOException {
        return getOrPost(uri, headers, null);
    }

    private static byte[] getOrPost(String uri, Headers headers, byte[] data) throws IOException {
        final URL url = new URL(uri);
        int numRetries = 3;
        for (;;) {
            long retryDelaySec = 5;
            try {
                return getOrPost(url, headers, data);
            } catch (Http.HttpError e) {
                int responseCode = e.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                    String retryAfter = e.getHeaders().getField("Retry-After");
                    if (retryAfter != null) {
                        retryDelaySec = Math.max(0, Long.valueOf(retryAfter));
                    }
                } else if (responseCode != HttpURLConnection.HTTP_GATEWAY_TIMEOUT) {
                    throw e;
                }
                if (numRetries-- <= 0) {
                    throw e;
                }
            } catch (IOException e) {
                if (numRetries-- <= 0) {
                    throw e;
                }
            }

            if (retryDelaySec > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(retryDelaySec));
                } catch (InterruptedException e) {
                    Clog.w(TAG, "Interrupted waiting for retry", e);
                    throw new IOException(e);
                }
            }
        }
    }

    private static byte[] getOrPost(URL url, Headers headers, byte[] data) throws IOException {
        HttpURLConnection connection = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        final int oldTag = TrafficStats.getThreadStatsTag();
        try {
            TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG);
            connection = (HttpURLConnection) url.openConnection();
            headers.apply(connection);

            if (data != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(data.length);

                outputStream = connection.getOutputStream();
                IoUtils.writeToStream(outputStream, data);
                checkResponseCode(connection);
            }

            checkResponseCode(connection);
            inputStream = connection.getInputStream();
            return IoUtils.readFromStream(inputStream);
        } finally {
            IoUtils.close(inputStream);
            IoUtils.close(outputStream);
            disconnect(connection);
            TrafficStats.setThreadStatsTag(oldTag);
        }
    }

    private static void checkResponseCode(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) return;
        String responseMessage = connection.getResponseMessage();
        Headers responseHeaders = new Headers(connection.getHeaderFields());

        InputStream errorStream = null;
        try {
            errorStream = connection.getErrorStream();
            if (errorStream != null) {
                byte[] responseBody = IoUtils.readFromStream(errorStream);
                throw new HttpError(responseCode, responseMessage, responseHeaders, responseBody);
            }
            throw new HttpError(responseCode, responseMessage, responseHeaders);
        } finally {
            IoUtils.close(errorStream);
        }
    }

    private static void disconnect(HttpURLConnection connection) {
        if (connection == null) return;
        connection.disconnect();
    }

    public static final class ContentType {
        private ContentType() { }
    }

    public static final class Headers {
        private final Map<String, List<String>> mFields;

        public static final Headers NONE = new Headers.Builder().build();

        private static Headers create(String contentType) {
            return new Headers.Builder().set("Content-Type", contentType).build();
        }

        private Headers(Map<String, List<String>> fields) {
            mFields = fields;
        }

        public void apply(@NonNull HttpURLConnection connection) {
            for (Map.Entry<String, List<String>> entry : mFields.entrySet()) {
                boolean first = true;
                String key = entry.getKey();
                for (String value: entry.getValue()) {
                    if (first) {
                        first = false;
                        connection.setRequestProperty(key, value);
                    } else {
                        connection.addRequestProperty(key, value);
                    }
                }
            }
        }

        public @Nullable String getField(@NonNull String key) {
            List<String> values = getFieldValues(key);
            return values == null ? null : values.get(0);
        }

        public @Nullable List<String> getFieldValues(@NonNull String key) {
            return getFields().get(key);
        }

        public @NonNull Map<String, List<String>> getFields() {
            return mFields;
        }

        public static final class Builder {
            private static final Comparator<String> FIELD_NAME_COMPARATOR = (a, b) -> {
                //noinspection StringEquality
                if (a == b) {
                    return 0;
                } else if (a == null) {
                    return -1;
                } else if (b == null) {
                    return 1;
                } else {
                    return String.CASE_INSENSITIVE_ORDER.compare(a, b);
                }
            };
            private final List<String> mNamesAndValues = new ArrayList<>();

            public Builder() { }

            public Builder(@NonNull Headers headers) {
                for (Map.Entry<String, List<String>> entry : headers.mFields.entrySet()) {
                    for (String value: entry.getValue()) {
                        mNamesAndValues.add(entry.getKey());
                        mNamesAndValues.add(value);
                    }
                }
            }

            public @NonNull Builder add(@NonNull String fieldName, @NonNull String value) {
                mNamesAndValues.add(fieldName);
                mNamesAndValues.add(value);
                return this;
            }

            public @NonNull Builder set(@NonNull String fieldName, @NonNull String value) {
                return removeAll(fieldName).add(fieldName, value);
            }

            private Builder removeAll(String fieldName) {
                for (int i = 0; i < mNamesAndValues.size(); i += 2) {
                    if (fieldName.equalsIgnoreCase(mNamesAndValues.get(i))) {
                        mNamesAndValues.remove(i);
                        mNamesAndValues.remove(i);
                    }
                }
                return this;
            }

            public @NonNull Headers build() {
                Map<String, List<String>> headers = new TreeMap<>(FIELD_NAME_COMPARATOR);

                for (int i = 0; i < mNamesAndValues.size(); i += 2) {
                    String fieldName = mNamesAndValues.get(i);
                    String value = mNamesAndValues.get(i + 1);

                    List<String> values = new ArrayList<>();
                    List<String> others = headers.get(fieldName);
                    if (others != null) {
                        values.addAll(others);
                    }
                    values.add(value);
                    headers.put(fieldName, Collections.unmodifiableList(values));
                }

                return new Headers(Collections.unmodifiableMap(headers));
            }
        }
    }

    public static final class HttpError extends IOException {
        private static final long serialVersionUID = 1L;

        private final int mCode;
        private final String mMessage;
        private final Headers mHeaders;
        private final byte[] mBody;

        private HttpError(int code, String message, Headers headers) {
            this(code, message, headers, null);
        }

        private HttpError(int code, String message, Headers headers, byte[] body) {
            super(code + " " + message);
            mCode = code;
            mMessage = message;
            mHeaders = headers;
            mBody = body;
        }

        public int getResponseCode() {
            return mCode;
        }

        public @NonNull String getResponseMessage() {
            return mMessage;
        }

        public @NonNull Headers getHeaders() {
            return mHeaders;
        }

        public @Nullable byte[] getResponseBody() {
            return mBody;
        }
    }
}
