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

package com.android.bips.ipp;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.bips.BuiltInPrintService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A persistent cache of certificate public keys known to be associated with certain printer
 * UUIDs.
 */
public class CertificateStore {
    private static final String TAG = CertificateStore.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** File location of the on-disk certificate store. */
    private final File mStoreFile;

    /** RAM-based store of certificates (UUID to certificate) */
    private final Map<String, byte[]> mCertificates = new HashMap<>();

    public CertificateStore(BuiltInPrintService service) {
        mStoreFile = new File(service.getCacheDir(), getClass().getSimpleName() + ".json");
        load();
    }

    /** Write a new, non-null certificate to the store. */
    public void put(String uuid, byte[] certificate) {
        byte[] oldCertificate = mCertificates.put(uuid, certificate);
        if (oldCertificate == null || !Arrays.equals(oldCertificate, certificate)) {
            // Cache the certificate for later
            if (DEBUG) Log.d(TAG, "New certificate uuid=" + uuid + " len=" + certificate.length);
            save();
        }
    }

    /** Remove any certificate associated with the specified UUID. */
    public void remove(String uuid) {
        if (mCertificates.remove(uuid) != null) {
            save();
        }
    }

    /** Return the known certificate public key for a printer having the specified UUID, or null. */
    public byte[] get(String uuid) {
        return mCertificates.get(uuid);
    }

    /** Write to storage immediately. */
    private void save() {
        if (mStoreFile.exists()) {
            mStoreFile.delete();
        }

        try (JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(mStoreFile)))) {
            writer.beginObject();
            writer.name("certificates");
            writer.beginArray();
            for (Map.Entry<String, byte[]> entry : mCertificates.entrySet()) {
                writer.beginObject();
                writer.name("uuid").value(entry.getKey());
                writer.name("pubkey").value(bytesToHex(entry.getValue()));
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            if (DEBUG) Log.d(TAG, "Wrote " + mCertificates.size() + " certificates to store");
        } catch (NullPointerException | IOException e) {
            Log.w(TAG, "Error while storing to " + mStoreFile, e);
        }
    }

    /** Load known certificates from storage into RAM. */
    private void load() {
        if (!mStoreFile.exists()) {
            return;
        }

        try (JsonReader reader = new JsonReader(new BufferedReader(new FileReader(mStoreFile)))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String itemName = reader.nextName();
                if (itemName.equals("certificates")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        loadItem(reader);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IllegalStateException | IOException error) {
            Log.w(TAG, "Error while loading from " + mStoreFile, error);
        }
        if (DEBUG) Log.d(TAG, "Loaded size=" + mCertificates.size() + " from " + mStoreFile);
    }

    /** Load a single certificate entry into RAM. */
    private void loadItem(JsonReader reader) throws IOException {
        String uuid = null;
        byte[] pubkey = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String itemName = reader.nextName();
            switch(itemName) {
                case "uuid":
                    uuid = reader.nextString();
                    break;
                case "pubkey":
                    try {
                        pubkey = hexToBytes(reader.nextString());
                    } catch (IllegalArgumentException ignored) {
                    }
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
        if (uuid != null && pubkey != null) {
            mCertificates.put(uuid, pubkey);
        }
    }

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /** Converts a byte array to a hexadecimal string, or null if bytes are null. */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[b >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[b & 0x0F];
        }
        return new String(hexChars);
    }

    /** Converts a hexadecimal string to a byte array, or null if hexString is null. */
    private static byte[] hexToBytes(String hexString) {
        if (hexString == null) {
            return null;
        }

        char[] source = hexString.toCharArray();
        byte[] dest = new byte[source.length / 2];
        for (int sourcePos = 0, destPos = 0; sourcePos < source.length; ) {
            int hi = Character.digit(source[sourcePos++], 16);
            int lo = Character.digit(source[sourcePos++], 16);
            if ((hi < 0) || (lo < 0)) {
                throw new IllegalArgumentException();
            }
            dest[destPos++] = (byte) (hi << 4 | lo);
        }
        return dest;
    }
}
