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

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.security.cryptauth.lib.securegcm.D2DConnectionContext;
import com.google.security.cryptauth.lib.securegcm.Ukey2Handshake;

import java.security.SignatureException;

/**
 * An {@link EncryptionRunner} that uses Ukey2 as the underlying implementation.
 */
public class Ukey2EncryptionRunner implements EncryptionRunner {

    private static final Ukey2Handshake.HandshakeCipher CIPHER =
            Ukey2Handshake.HandshakeCipher.P256_SHA512;

    private static final int AUTH_STRING_LENGTH = 6;

    private Ukey2Handshake mUkey2client;
    private boolean mRunnerIsInvalid;

    @Override
    public HandshakeMessage initHandshake() {
        checkRunnerIsNew();
        try {
            mUkey2client = Ukey2Handshake.forInitiator(CIPHER);
            return HandshakeMessage.newBuilder()
                    .setHandshakeState(getHandshakeState())
                    .setNextMessage(mUkey2client.getNextHandshakeMessage())
                    .build();
        } catch (com.google.security.cryptauth.lib.securegcm.HandshakeException e) {
            Log.e(TAG, "unexpected exception", e);
            throw new RuntimeException(e);
        }

    }

    @Override
    public HandshakeMessage respondToInitRequest(byte[] initializationRequest)
            throws HandshakeException {
        checkRunnerIsNew();
        try {
            if (mUkey2client != null) {
                throw new IllegalStateException("Cannot reuse encryption runners, "
                        + "this one is already initialized");
            }
            mUkey2client = Ukey2Handshake.forResponder(CIPHER);
            mUkey2client.parseHandshakeMessage(initializationRequest);
            return HandshakeMessage.newBuilder()
                    .setHandshakeState(getHandshakeState())
                    .setNextMessage(mUkey2client.getNextHandshakeMessage())
                    .build();

        } catch (com.google.security.cryptauth.lib.securegcm.HandshakeException
                | Ukey2Handshake.AlertException e) {
            throw new HandshakeException(e);
        }
    }

    private void checkRunnerIsNew() {
        if (mUkey2client != null) {
            throw new IllegalStateException("This runner is already initialized.");
        }
    }


    @Override
    public HandshakeMessage continueHandshake(byte[] response) throws HandshakeException {
        checkInitialized();
        try {
            if (mUkey2client.getHandshakeState() != Ukey2Handshake.State.IN_PROGRESS) {
                throw new IllegalStateException("handshake is not in progress, state ="
                        + mUkey2client.getHandshakeState());
            }
            mUkey2client.parseHandshakeMessage(response);

            // Not obvious from ukey2 api, but getting the next message can change the state.
            // calling getNext message might go from in progress to verification needed, on
            // the assumption that we already send this message to the peer.
            byte[] nextMessage = null;
            if (mUkey2client.getHandshakeState() == Ukey2Handshake.State.IN_PROGRESS) {
                nextMessage = mUkey2client.getNextHandshakeMessage();
            }

            String verificationCode = null;
            if (mUkey2client.getHandshakeState() == Ukey2Handshake.State.VERIFICATION_NEEDED) {
                verificationCode = generateReadablePairingCode(
                        mUkey2client.getVerificationString(AUTH_STRING_LENGTH));
            }
            return HandshakeMessage.newBuilder()
                    .setHandshakeState(getHandshakeState())
                    .setNextMessage(nextMessage)
                    .setVerificationCode(verificationCode)
                    .build();
        } catch (com.google.security.cryptauth.lib.securegcm.HandshakeException
                | Ukey2Handshake.AlertException e) {
            throw new HandshakeException(e);
        }
    }

    /**
     * Returns a human-readable pairing code string generated from the verification bytes. Converts
     * each byte into a digit with a simple modulo.
     *
     * <p>This should match the implementation in the iOS and Android client libraries.
     */
    @VisibleForTesting
    String generateReadablePairingCode(byte[] verificationCode) {
        StringBuilder outString = new StringBuilder();
        for (byte b : verificationCode) {
            int unsignedInt = Byte.toUnsignedInt(b);
            int digit = unsignedInt % 10;
            outString.append(digit);
        }

        return outString.toString();
    }

    private static class UKey2Key implements Key {

        private final D2DConnectionContext mConnectionContext;

        UKey2Key(@NonNull D2DConnectionContext connectionContext) {
            this.mConnectionContext = connectionContext;
        }

        @Override
        public byte[] asBytes() {
            return mConnectionContext.saveSession();
        }

        @Override
        public byte[] encryptData(byte[] data) {
            return mConnectionContext.encodeMessageToPeer(data);
        }

        @Override
        public byte[] decryptData(byte[] encryptedData) throws SignatureException {
            return mConnectionContext.decodeMessageFromPeer(encryptedData);
        }
    }

    @Override
    public HandshakeMessage verifyPin() throws HandshakeException {
        checkInitialized();
        mUkey2client.verifyHandshake();
        try {
            return HandshakeMessage.newBuilder()
                    .setHandshakeState(getHandshakeState())
                    .setKey(new UKey2Key(mUkey2client.toConnectionContext()))
                    .build();
        } catch (com.google.security.cryptauth.lib.securegcm.HandshakeException e) {
            throw new HandshakeException(e);
        }
    }

    @HandshakeMessage.HandshakeState
    private int getHandshakeState() {
        checkInitialized();
        switch (mUkey2client.getHandshakeState()) {
            case ALREADY_USED:
            case ERROR:
                throw new IllegalStateException("unexpected error state");
            case FINISHED:
                return HandshakeMessage.HandshakeState.FINISHED;
            case IN_PROGRESS:
                return HandshakeMessage.HandshakeState.IN_PROGRESS;
            case VERIFICATION_IN_PROGRESS:
            case VERIFICATION_NEEDED:
                return HandshakeMessage.HandshakeState.VERIFICATION_NEEDED;
            default:
                throw new IllegalStateException("unexpected handshake state");
        }
    }

    @Override
    public Key keyOf(byte[] serialized) {
        return new UKey2Key(D2DConnectionContext.fromSavedSession(serialized));
    }

    @Override
    public void invalidPin() {
        mRunnerIsInvalid = true;
    }

    private UKey2Key checkIsUkey2Key(Key key) {
        if (!(key instanceof UKey2Key)) {
            throw new IllegalArgumentException("wrong key type");
        }
        return (UKey2Key) key;
    }

    private void checkInitialized() {
        if (mUkey2client == null) {
            throw new IllegalStateException("runner not initialized");
        }
        if (mRunnerIsInvalid) {
            throw new IllegalStateException("runner has been invalidated");
        }
    }
}
