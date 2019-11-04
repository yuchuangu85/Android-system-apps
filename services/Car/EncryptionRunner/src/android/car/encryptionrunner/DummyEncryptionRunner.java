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

import android.annotation.IntDef;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An encryption runner that doesn't actually do encryption. Useful for debugging. Do not use in
 * production environments.
 */
@VisibleForTesting
public class DummyEncryptionRunner implements EncryptionRunner {

    private static final String KEY = "key";
    @VisibleForTesting
    public static final String INIT = "init";
    @VisibleForTesting
    public static final String INIT_RESPONSE = "initResponse";
    @VisibleForTesting
    public static final String CLIENT_RESPONSE = "clientResponse";
    public static final String VERIFICATION_CODE = "1234";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Mode.UNKNOWN, Mode.CLIENT, Mode.SERVER})
    private @interface Mode {

        int UNKNOWN = 0;
        int CLIENT = 1;
        int SERVER = 2;
    }

    @Mode
    private int mMode;
    @HandshakeMessage.HandshakeState
    private int mState;

    @Override
    public HandshakeMessage initHandshake() {
        checkRunnerIsNew();
        mMode = Mode.CLIENT;
        mState = HandshakeMessage.HandshakeState.IN_PROGRESS;
        return HandshakeMessage.newBuilder()
                .setHandshakeState(mState)
                .setNextMessage(INIT.getBytes())
                .build();
    }

    @Override
    public HandshakeMessage respondToInitRequest(byte[] initializationRequest)
            throws HandshakeException {
        checkRunnerIsNew();
        mMode = Mode.SERVER;
        if (!new String(initializationRequest).equals(INIT)) {
            throw new HandshakeException("Unexpected initialization request");
        }
        mState = HandshakeMessage.HandshakeState.IN_PROGRESS;
        return HandshakeMessage.newBuilder()
                .setHandshakeState(HandshakeMessage.HandshakeState.IN_PROGRESS)
                .setNextMessage(INIT_RESPONSE.getBytes())
                .build();
    }

    private void checkRunnerIsNew() {
        if (mState != HandshakeMessage.HandshakeState.UNKNOWN) {
            throw new IllegalStateException("runner already initialized.");
        }
    }

    @Override
    public HandshakeMessage continueHandshake(byte[] response) throws HandshakeException {
        if (mState != HandshakeMessage.HandshakeState.IN_PROGRESS) {
            throw new HandshakeException("not waiting for response but got one");
        }
        switch(mMode) {
            case Mode.SERVER:
                if (!CLIENT_RESPONSE.equals(new String(response))) {
                    throw new HandshakeException("unexpected response: " + new String(response));
                }
                mState = HandshakeMessage.HandshakeState.VERIFICATION_NEEDED;
                return HandshakeMessage.newBuilder()
                        .setVerificationCode(VERIFICATION_CODE)
                        .setHandshakeState(mState)
                        .build();
            case Mode.CLIENT:
                if (!INIT_RESPONSE.equals(new String(response))) {
                    throw new HandshakeException("unexpected response: " + new String(response));
                }
                mState = HandshakeMessage.HandshakeState.VERIFICATION_NEEDED;
                return HandshakeMessage.newBuilder()
                        .setHandshakeState(mState)
                        .setNextMessage(CLIENT_RESPONSE.getBytes())
                        .setVerificationCode(VERIFICATION_CODE)
                        .build();
            default:
                throw new IllegalStateException("unexpected state: "  + mState);
        }
    }

    @Override
    public Key keyOf(byte[] serialized) {
        return new DummyKey();
    }

    @Override
    public HandshakeMessage verifyPin() throws HandshakeException {
        if (mState != HandshakeMessage.HandshakeState.VERIFICATION_NEEDED) {
            throw new IllegalStateException("asking to verify pin, state = " + mState);
        }
        mState = HandshakeMessage.HandshakeState.FINISHED;
        return HandshakeMessage.newBuilder()
                .setHandshakeState(mState)
                .setKey(new DummyKey())
                .build();
    }

    @Override
    public void invalidPin() {
        mState = HandshakeMessage.HandshakeState.INVALID;
    }

    private class DummyKey implements Key {

        @Override
        public byte[] asBytes() {
            return KEY.getBytes();
        }

        @Override
        public byte[] encryptData(byte[] data) {
            return data;
        }

        @Override
        public byte[] decryptData(byte[] encryptedData) {
            return encryptedData;
        }
    }
}
