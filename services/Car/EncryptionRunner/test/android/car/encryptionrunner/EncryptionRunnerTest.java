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

package android.car.encryptionrunner;

import static com.google.common.truth.Truth.assertThat;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EncryptionRunnerTest {

    private static final byte[] sTestData = "test data".getBytes();
    private interface RunnerFactory {
        EncryptionRunner newRunner();
    }

    @Test
    public void happyFlow_dummyRunner() throws Exception {
        verifyRunners(EncryptionRunnerFactory::newDummyRunner);
    }

    @Test
    public void happyFlow_ukey2Runner() throws Exception {
        verifyRunners(EncryptionRunnerFactory::newRunner);
    }

    /**
     * Runs through a happy flow of encryption runners and verifies that they behave as expected.
     * Some * of the test is implementation specific because the interface doesn't specify how many
     * round * trips may be needed but this test makes assumptions( i.e. white box testing).
     */
    private void verifyRunners(RunnerFactory runnerFactory) throws Exception {
        EncryptionRunner clientRunner = runnerFactory.newRunner();
        EncryptionRunner serverRunner = runnerFactory.newRunner();

        verifyHandshake(clientRunner, serverRunner);

        HandshakeMessage finalServerMessage = serverRunner.verifyPin();
        assertThat(finalServerMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.FINISHED);
        assertThat(finalServerMessage.getKey()).isNotNull();
        assertThat(finalServerMessage.getNextMessage()).isNull();

        HandshakeMessage finalClientMessage = clientRunner.verifyPin();
        assertThat(finalClientMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.FINISHED);
        assertThat(finalClientMessage.getKey()).isNotNull();
        assertThat(finalClientMessage.getNextMessage()).isNull();

        assertThat(finalServerMessage.getKey()
                .decryptData(finalClientMessage.getKey().encryptData(sTestData)))
                .isEqualTo(sTestData);
        assertThat(finalClientMessage.getKey()
                .decryptData(finalServerMessage.getKey().encryptData(sTestData)))
                .isEqualTo(sTestData);
    }

    private void verifyHandshake(EncryptionRunner clientRunner, EncryptionRunner serverRunner)
            throws Exception {
        HandshakeMessage initialClientMessage = clientRunner.initHandshake();

        assertThat(initialClientMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.IN_PROGRESS);
        assertThat(initialClientMessage.getKey()).isNull();
        assertThat(initialClientMessage.getNextMessage()).isNotNull();

        // This and the following similar log statements are useful when running this test to
        // find the payload sizes.
        Log.i(EncryptionRunner.TAG,
                "initial client size:" + initialClientMessage.getNextMessage().length);

        HandshakeMessage initialServerMessage =
                serverRunner.respondToInitRequest(initialClientMessage.getNextMessage());

        assertThat(initialServerMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.IN_PROGRESS);
        assertThat(initialServerMessage.getKey()).isNull();
        assertThat(initialServerMessage.getNextMessage()).isNotNull();

        Log.i(EncryptionRunner.TAG,
                "initial server message size:" + initialServerMessage.getNextMessage().length);

        HandshakeMessage clientMessage =
                clientRunner.continueHandshake(initialServerMessage.getNextMessage());

        assertThat(clientMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.VERIFICATION_NEEDED);
        assertThat(clientMessage.getKey()).isNull();
        assertThat(clientMessage.getVerificationCode()).isNotEmpty();
        assertThat(clientMessage.getNextMessage()).isNotNull();

        Log.i(EncryptionRunner.TAG,
                "second client message size:" + clientMessage.getNextMessage().length);

        HandshakeMessage serverMessage =
                serverRunner.continueHandshake(clientMessage.getNextMessage());
        assertThat(serverMessage.getHandshakeState())
                .isEqualTo(HandshakeMessage.HandshakeState.VERIFICATION_NEEDED);
        assertThat(serverMessage.getKey()).isNull();
        assertThat(serverMessage.getNextMessage()).isNull();

        Log.i(EncryptionRunner.TAG,
                "last server message size:" + clientMessage.getNextMessage().length);
    }

    @Test
    public void invalidPin_ukey2() throws Exception {
        invalidPinTest(EncryptionRunnerFactory::newRunner);
    }

    @Test
    public void invalidPin_dummy() throws Exception {
        invalidPinTest(EncryptionRunnerFactory::newDummyRunner);
    }

    private void invalidPinTest(RunnerFactory runnerFactory) throws Exception {
        EncryptionRunner clientRunner = runnerFactory.newRunner();
        EncryptionRunner serverRunner = runnerFactory.newRunner();

        verifyHandshake(clientRunner, serverRunner);
        clientRunner.invalidPin();
        serverRunner.invalidPin();

        try {
            clientRunner.verifyPin();
            Assert.fail();
        } catch (Exception ignored) {
            // pass
        }

        try {
            serverRunner.verifyPin();
            Assert.fail();
        } catch (Exception ignored) {
            // pass
        }
    }
}
