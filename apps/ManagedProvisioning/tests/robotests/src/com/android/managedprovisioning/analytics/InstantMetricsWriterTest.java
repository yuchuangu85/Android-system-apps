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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.admin.DevicePolicyEventLogger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link InstantMetricsWriter}.
 */
@RunWith(JUnit4.class)
public class InstantMetricsWriterTest {
    @Test
    public void write_callsWriteImmediately() {
        final MetricsWriter writer = new InstantMetricsWriter();
        final DevicePolicyEventLogger devicePolicyEventLogger = mock(DevicePolicyEventLogger.class);

        writer.write(devicePolicyEventLogger);

        verify(devicePolicyEventLogger).write();
        verifyNoMoreInteractions(devicePolicyEventLogger);
    }
}
