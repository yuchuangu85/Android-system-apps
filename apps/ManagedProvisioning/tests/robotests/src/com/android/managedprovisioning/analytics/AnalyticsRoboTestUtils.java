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
 * limitations under the License
 */

package com.android.managedprovisioning.analytics;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.app.admin.DevicePolicyEventLogger;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Objects;

class AnalyticsRoboTestUtils {
    static void assertDevicePolicyEventLoggerEqual(
            @NonNull DevicePolicyEventLogger logger1, @NonNull DevicePolicyEventLogger logger2) {
        assertThat(logger1).isNotNull();
        assertThat(logger2).isNotNull();
        assertAdminPackageNamesEqual(logger1, logger2);
        assertThat(logger1.getBoolean()).isEqualTo(logger2.getBoolean());
        assertThat(logger1.getInt()).isEqualTo(logger2.getInt());
        assertThat(logger1.getEventId()).isEqualTo(logger2.getEventId());
        assertThat(logger1.getTimePeriod()).isEqualTo(logger2.getTimePeriod());
        assertStringArraysEqual(logger1, logger2);
    }

    /**
     * For string types, protos write an empty string instead of nulls.
     */
    private static void assertAdminPackageNamesEqual(
            DevicePolicyEventLogger logger1, DevicePolicyEventLogger logger2) {
        if (logger1.getAdminPackageName() == null || logger2.getAdminPackageName() == null) {
            assertThat(TextUtils.isEmpty(logger1.getAdminPackageName())).isTrue();
            assertThat(TextUtils.isEmpty(logger2.getAdminPackageName())).isTrue();
        } else {
            assertThat(logger1.getAdminPackageName()).isEqualTo(logger2.getAdminPackageName());
        }
    }

    /**
     * Since protos drop null values when writing string arrays, we ignore them.
     *
     * <p>If the string array is, for example <code>[null, null, "abc", null]</code>,
     * when it gets written to the proto, it's then read as ["abc"], because protos drop null
     * values. Since we are comparing the array we've written and the array we've read, we must
     * account for that.
     *
     * <p>Note that if the array is <code>[null, null, null]</code>, it's never
     * written to the proto, so the expected read value is a <code>null</code> array.
     */
    private static void assertStringArraysEqual(
            DevicePolicyEventLogger logger1, DevicePolicyEventLogger logger2) {
        final String[] strings1 = sanitize(logger1.getStringArray());
        final String[] strings2 = sanitize(logger2.getStringArray());

        if (strings1 == null || strings2 == null) {
            assertThat(strings1).isNull();
            assertThat(strings2).isNull();
            return;
        }

        assertThat(strings1.length).isEqualTo(strings2.length);
        for (int i = 0; i < strings1.length; i++) {
            assertThat(strings1[i]).isEqualTo(strings2[i]);
        }
    }

    /**
     * Strips the array of null values.
     *
     * <p><p><i>Examples</i>:
     * <p>If array1 is <code>["abc", "def"]</code> and array2 is <code>["abc", "def"]</code>,
     * we return <code>true</code>.
     *
     * <p>If array1 is <code>[null, "abc", null]</code> and array2 is <code>["abc"]</code> (or vice
     * versa), we return <code>true</code>.
     *
     * <p>If array1 is <code>[null, null, null]</code> and array2 is <code>null</code> (or vice
     * versa), we return <code>true</code>.
     */
    private static String[] sanitize(String[] stringArray) {
        if (stringArray == null) {
            return null;
        }
        final String[] strippedArray =
                Arrays.stream(stringArray).filter(Objects::nonNull).toArray(String[]::new);
        if (strippedArray.length == 0) {
            return null;
        }
        return strippedArray;
    }

    static void assertDevicePolicyEventLoggersEqual(
            DevicePolicyEventLogger[] loggers1, DevicePolicyEventLogger[] loggers2) {
        assertThat(loggers1).isNotNull();
        assertThat(loggers2).isNotNull();
        assertThat(loggers1.length).isEqualTo(loggers2.length);
        for (int i = 0; i < loggers1.length; i++) {
            assertDevicePolicyEventLoggerEqual(loggers1[i], loggers2[i]);
        }
    }
}
