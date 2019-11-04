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

package android.car.encryptionrunner;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Factory that creates encryption runner.
 */
public class EncryptionRunnerFactory {

    private EncryptionRunnerFactory() {
        // prevent instantiation.
    }

    /**
     * Creates a new {@link EncryptionRunner}.
     */
    public static EncryptionRunner newRunner() {
        return new Ukey2EncryptionRunner();
    }

    /**
     * Creates a new {@link EncryptionRunner} one that doesn't actually do encryption but is useful
     * for testing.
     */
    @VisibleForTesting
    public static EncryptionRunner newDummyRunner() {
        return new DummyEncryptionRunner();
    }
}
