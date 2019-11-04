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

package com.android.car.settings.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Utf8ByteLengthFilter}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class Utf8ByteLengthFilterTest {

    @Test
    public void filter_belowMaxBytes_returnsNull() {
        CharSequence source = "1"; // 1 byte.
        SpannableStringBuilder dest = new SpannableStringBuilder("abcdefgh"); // 8 bytes.
        InputFilter lengthFilter = new Utf8ByteLengthFilter(10);

        // Append source to dest.
        CharSequence filtered = lengthFilter.filter(source, /* start= */ 0, source.length(), dest,
                dest.length(), dest.length());

        // Source is not filtered.
        assertThat(filtered).isNull();
    }

    @Test
    public void filter_maxBytes_returnsNull() {
        CharSequence source = "1"; // 1 byte.
        SpannableStringBuilder dest = new SpannableStringBuilder("abcdefghi"); // 9 bytes.
        InputFilter lengthFilter = new Utf8ByteLengthFilter(10);

        // Append source to dest.
        CharSequence filtered = lengthFilter.filter(source, /* start= */ 0, source.length(), dest,
                dest.length(), dest.length());

        // Source is not filtered.
        assertThat(filtered).isNull();
    }

    @Test
    public void filter_aboveMaxBytes_returnsFilteredSource() {
        CharSequence source = "12"; // 2 bytes.
        SpannableStringBuilder dest = new SpannableStringBuilder("abcdefghi"); // 8 bytes.
        InputFilter lengthFilter = new Utf8ByteLengthFilter(10);

        // Append source to dest.
        CharSequence filtered = lengthFilter.filter(source, /* start= */ 0, source.length(), dest,
                dest.length(), dest.length());

        // Source is filtered.
        assertThat(filtered).isEqualTo("1");
    }

    // Borrowed from com.android.settings.bluetooth.Utf8ByteLengthFilterTest.
    @Test
    public void exerciseFilter() {
        CharSequence source;
        SpannableStringBuilder dest;
        InputFilter lengthFilter = new Utf8ByteLengthFilter(10);
        InputFilter[] filters = {lengthFilter};

        source = "abc";
        dest = new SpannableStringBuilder("abcdefgh");
        dest.setFilters(filters);

        dest.insert(1, source);
        String expectedString1 = "aabbcdefgh";
        assertThat(dest.toString()).isEqualTo(expectedString1);

        dest.replace(5, 8, source);
        String expectedString2 = "aabbcabcgh";
        assertThat(dest.toString()).isEqualTo(expectedString2);

        dest.insert(2, source);
        assertThat(dest.toString()).isEqualTo(expectedString2);

        dest.delete(1, 3);
        String expectedString3 = "abcabcgh";
        assertThat(dest.toString()).isEqualTo(expectedString3);

        dest.append("12345");
        String expectedString4 = "abcabcgh12";
        assertThat(dest.toString()).isEqualTo(expectedString4);

        source = "\u60a8\u597d";  // 2 Chinese chars == 6 bytes in UTF-8
        dest.replace(8, 10, source);
        assertThat(dest.toString()).isEqualTo(expectedString3);

        dest.replace(0, 1, source);
        String expectedString5 = "\u60a8bcabcgh";
        assertThat(dest.toString()).isEqualTo(expectedString5);

        dest.replace(0, 4, source);
        String expectedString6 = "\u60a8\u597dbcgh";
        assertThat(dest.toString()).isEqualTo(expectedString6);

        source = "\u00a3\u00a5";  // 2 Latin-1 chars == 4 bytes in UTF-8
        dest.delete(2, 6);
        dest.insert(0, source);
        String expectedString7 = "\u00a3\u00a5\u60a8\u597d";
        assertThat(dest.toString()).isEqualTo(expectedString7);

        dest.replace(2, 3, source);
        String expectedString8 = "\u00a3\u00a5\u00a3\u597d";
        assertThat(dest.toString()).isEqualTo(expectedString8);

        dest.replace(3, 4, source);
        String expectedString9 = "\u00a3\u00a5\u00a3\u00a3\u00a5";
        assertThat(dest.toString()).isEqualTo(expectedString9);
    }
}
