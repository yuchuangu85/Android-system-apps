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

package com.android.phone.ecc;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.phone.ecc.nano.ProtobufEccData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

/**
 * Unit tests for eccdata.
 */
@RunWith(AndroidJUnit4.class)
public class EccDataTest extends TelephonyTestBase {
    @Test
    public void testEccDataContent() throws IOException {
        InputStream eccData = new GZIPInputStream(new BufferedInputStream(
                InstrumentationRegistry.getTargetContext().getAssets().open("eccdata")));
        ProtobufEccData.AllInfo allEccMessages = ProtobufEccData.AllInfo.parseFrom(
                readInputStreamToByteArray(eccData));
        eccData.close();

        HashSet loadedIsos = new HashSet(300);
        HashSet loadedNumbers = new HashSet(5);

        for (ProtobufEccData.CountryInfo countryInfo : allEccMessages.countries) {
            assertThat(countryInfo.isoCode).isNotEmpty();
            assertThat(countryInfo.isoCode).isEqualTo(countryInfo.isoCode.toUpperCase().trim());
            assertThat(loadedIsos.contains(countryInfo.isoCode)).isFalse();
            loadedIsos.add(countryInfo.isoCode);

            loadedNumbers.clear();
            for (ProtobufEccData.EccInfo eccInfo : countryInfo.eccs) {
                assertThat(eccInfo.phoneNumber).isNotEmpty();
                assertThat(eccInfo.phoneNumber).isEqualTo(eccInfo.phoneNumber.trim());
                assertThat(loadedNumbers.contains(eccInfo.phoneNumber)).isFalse();
                assertThat(eccInfo.types).isNotEmpty();
                loadedNumbers.add(eccInfo.phoneNumber);
            }
        }
    }

    /**
     * Util function to convert inputStream to byte array before parsing proto data.
     */
    private static byte[] readInputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16 * 1024]; // Read 16k chunks
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}
