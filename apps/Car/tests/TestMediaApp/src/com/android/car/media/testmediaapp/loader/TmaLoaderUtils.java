/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.media.testmediaapp.loader;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TmaLoaderUtils {

    private static final String TAG = "TmaLoaderUtils";

    /** The default buffer size to use to read files. */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final String[] NO_STRINGS = {};

    private TmaLoaderUtils() {
    }

    @Nullable
    static JSONObject jsonFromAsset(Context context, String assetPathName) {
        String jsonString = stringFromAsset(context, assetPathName);
        try {
            return (jsonString != null) ? new JSONObject(jsonString) : null;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to convert to json object: " + e);
            return null;
        }
    }

    /** Returns a map from the enum value names to the enum values. */
    static <T extends Enum> Map<String, T> enumNamesToValues(T[] values) {
        Map<String, T> result = new HashMap<>();
        for (T val : values) {
            result.put(val.name(), val);
        }
        return result;
    }

    /** Returns the enum value mapped to the name of the given key, or fallback if missing. */
    @Nullable
    static <K extends Enum, E extends Enum> E getEnum(
            JSONObject json, K key, Map<String, E> enumMap, E fallback) {
        E result = enumMap.get(getString(json, key));
        return (result != null) ? result : fallback;
    }

    /** Returns the array mapped to the name of the given key, or null if missing. */
    @Nullable
    static <T extends Enum> JSONArray getArray(JSONObject json, T key) {
        try {
            return json.has(key.name()) ? json.getJSONArray(key.name()) : null;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException getting array for: " + key + " e: " + e);
            return null;
        }
    }

    /** Returns the array mapped to the name of the given key, or empty if missing. */
    static <T extends Enum, U extends Enum> List<U> getEnumArray(JSONObject json, T key,
            Map<String, U> enumMap) {
        try {
            JSONArray array = json.has(key.name()) ? json.getJSONArray(key.name()) : null;
            int count = (array != null) ? array.length() : 0;
            List<U> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add(enumMap.get(array.getString(i)));
            }
            return result;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException getting array for: " + key + " e: " + e);
            return new ArrayList<>();
        }
    }

    /** Returns the string mapped to the name of the given key, or null if missing. */
    @Nullable
    static <T extends Enum> String getString(JSONObject json, T key) {
        try {
            return json.has(key.name()) ? json.getString(key.name()) : null;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException getting string for: " + key + " e: " + e);
            return null;
        }
    }

    /** Returns the integer mapped to the name of the given key, or 0 if missing. */
    static <T extends Enum> int getInt(JSONObject json, T key) {
        try {
            return json.has(key.name()) ? json.getInt(key.name()) : 0;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException getting int for: " + key + " e: " + e);
            return 0;
        }
    }

    /** Takes a | separated list of flags and turns it into a bitfield value. */
    static int parseFlags(@Nullable String jsonFlags, Map<String, Integer> flagsMap) {
        int result = 0;
        String[] flags = (jsonFlags != null) ? jsonFlags.split("\\|") : NO_STRINGS;
        for (String flag : flags) {
            Integer value = flagsMap.get(flag);
            if (value != null) {
                result |= value;
            } else {
                Log.e(TAG, "Unknown flag: " + flag);
            }
        }
        return result;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
    }

    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    @Nullable
    private static String stringFromAsset(Context context, String assetPathName) {
        InputStream stream = null;
        try {
            stream = context.getAssets().open(assetPathName);
            if (stream == null) {
                return null;
            }
            return new String(toByteArray(stream));
        } catch (IOException e) {
            Log.e(TAG, "failed to load string from asset: " + e);
            return null;
        } finally {
            close(stream);
        }
    }

    private static void close(@Nullable Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            Log.e(TAG, "Error in close(): " + e);
        }
    }
}
