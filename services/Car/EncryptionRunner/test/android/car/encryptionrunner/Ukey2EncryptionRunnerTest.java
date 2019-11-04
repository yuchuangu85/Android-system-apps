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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Ukey2EncryptionRunnerTest {

    private Ukey2EncryptionRunner mRunner;

    @Before
    public void setup() {
        mRunner = new Ukey2EncryptionRunner();
    }

    @Test
    public void generateReadablePairingCode_modsBytesAcrossRange() throws Exception {
        // 194 is an example of a value that would fail if using signed instead of unsigned ints
        // 194 -> 11000010
        // 11000010 -> 194 (unsigned 8-bit int)
        // 11000010 -> -62 (signed 8-bit int)
        byte[] bytes = new byte[]{0, 7, (byte) 161, (byte) 194, (byte) 196, (byte) 255};
        String pairingCode = mRunner.generateReadablePairingCode(bytes);

        assertThat(pairingCode).isEqualTo("071465");
    }
}
