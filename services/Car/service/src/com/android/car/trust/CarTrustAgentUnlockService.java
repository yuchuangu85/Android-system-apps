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

package com.android.car.trust;

import static com.android.car.trust.EventLog.CLIENT_AUTHENTICATED;
import static com.android.car.trust.EventLog.RECEIVED_DEVICE_ID;
import static com.android.car.trust.EventLog.REMOTE_DEVICE_CONNECTED;
import static com.android.car.trust.EventLog.START_UNLOCK_ADVERTISING;
import static com.android.car.trust.EventLog.STOP_UNLOCK_ADVERTISING;
import static com.android.car.trust.EventLog.UNLOCK_CREDENTIALS_RECEIVED;
import static com.android.car.trust.EventLog.UNLOCK_ENCRYPTION_STATE;
import static com.android.car.trust.EventLog.UNLOCK_SERVICE_INIT;
import static com.android.car.trust.EventLog.WAITING_FOR_CLIENT_AUTH;
import static com.android.car.trust.EventLog.logUnlockEvent;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.car.encryptionrunner.EncryptionRunner;
import android.car.encryptionrunner.EncryptionRunnerFactory;
import android.car.encryptionrunner.HandshakeException;
import android.car.encryptionrunner.HandshakeMessage;
import android.car.encryptionrunner.Key;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.car.BLEStreamProtos.BLEOperationProto.OperationType;
import com.android.car.PhoneAuthProtos.PhoneAuthProto.PhoneCredentials;
import com.android.car.Utils;
import com.android.car.protobuf.InvalidProtocolBufferException;
import com.android.internal.annotations.GuardedBy;

import com.google.security.cryptauth.lib.securegcm.D2DConnectionContext;
import com.google.security.cryptauth.lib.securemessage.CryptoOps;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

/**
 * A service that interacts with the Trust Agent {@link CarBleTrustAgent} and a comms (BLE) service
 * {@link CarTrustAgentBleManager} to receive the necessary credentials to authenticate
 * an Android user.
 *
 * <p>
 * The unlock flow is as follows:
 * <ol>
 * <li>IHU advertises via BLE when it is in a locked state.  The advertisement includes its
 * identifier.
 * <li>Phone (Trusted device) scans, finds and connects to the IHU.
 * <li>Protocol versions are exchanged and verified.
 * <li>Phone sends its identifier in plain text.
 * <li>IHU verifies that the phone is enrolled as a trusted device from its identifier.
 * <li>IHU, then sends an ACK back to the phone.
 * <li>Phone & IHU go over the key exchange (using UKEY2) for encrypting this new session.
 * <li>Key exchange is completed without any numeric comparison.
 * <li>Phone sends its MAC (digest) that is computed from the context from this new session and the
 * previous session.
 * <li>IHU computes Phone's MAC and validates against what the phone sent.  On validation failure,
 * the stored encryption keys for the phone are deleted.  This would require the phone to re-enroll
 * again.
 * <li>IHU sends its MAC that is computed similarly from the new session and previous session
 * contexts.
 * <li>Phone computes IHU's MAC internally and validates it against what it received.
 * <li>At this point, the devices have mutually authenticated each other and also have keys to
 * encrypt
 * current session.
 * <li>IHU saves the current session keys.  This would serve for authenticating the next session.
 * <li>Phone sends the encrypted escrow token and handle to the IHU.
 * <li>IHU retrieves the user id and authenticates the user.
 * </ol>
 */
public class CarTrustAgentUnlockService {
    private static final String TAG = "CarTrustAgentUnlock";
    private static final String TRUSTED_DEVICE_UNLOCK_ENABLED_KEY = "trusted_device_unlock_enabled";

    // Arbitrary log size
    private static final int MAX_LOG_SIZE = 20;
    private static final byte[] RESUME = "RESUME".getBytes();
    private static final byte[] SERVER = "SERVER".getBytes();
    private static final byte[] CLIENT = "CLIENT".getBytes();
    private static final int RESUME_HMAC_LENGTH = 32;

    private static final byte[] ACKNOWLEDGEMENT_MESSAGE = "ACK".getBytes();

    // State of the unlock process.  Important to maintain the same order in both phone and IHU.
    // State increments to the next state on successful completion.
    private static final int UNLOCK_STATE_WAITING_FOR_UNIQUE_ID = 0;
    private static final int UNLOCK_STATE_KEY_EXCHANGE_IN_PROGRESS = 1;
    private static final int UNLOCK_STATE_WAITING_FOR_CLIENT_AUTH = 2;
    private static final int UNLOCK_STATE_MUTUAL_AUTH_ESTABLISHED = 3;
    private static final int UNLOCK_STATE_PHONE_CREDENTIALS_RECEIVED = 4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"UNLOCK_STATE_"}, value = {UNLOCK_STATE_WAITING_FOR_UNIQUE_ID,
            UNLOCK_STATE_KEY_EXCHANGE_IN_PROGRESS, UNLOCK_STATE_WAITING_FOR_CLIENT_AUTH,
            UNLOCK_STATE_MUTUAL_AUTH_ESTABLISHED, UNLOCK_STATE_PHONE_CREDENTIALS_RECEIVED})
    @interface UnlockState {
    }

    @UnlockState
    private int mCurrentUnlockState = UNLOCK_STATE_WAITING_FOR_UNIQUE_ID;

    private final CarTrustedDeviceService mTrustedDeviceService;
    private final CarTrustAgentBleManager mCarTrustAgentBleManager;
    private CarTrustAgentUnlockDelegate mUnlockDelegate;
    private String mClientDeviceId;
    private final Queue<String> mLogQueue = new LinkedList<>();

    // Locks
    private final Object mDeviceLock = new Object();

    @GuardedBy("mDeviceLock")
    private BluetoothDevice mRemoteUnlockDevice;

    private EncryptionRunner mEncryptionRunner = EncryptionRunnerFactory.newRunner();
    private HandshakeMessage mHandshakeMessage;
    private Key mEncryptionKey;
    @HandshakeMessage.HandshakeState
    private int mEncryptionState = HandshakeMessage.HandshakeState.UNKNOWN;

    private D2DConnectionContext mPrevContext;
    private D2DConnectionContext mCurrentContext;

    CarTrustAgentUnlockService(CarTrustedDeviceService service,
            CarTrustAgentBleManager bleService) {
        mTrustedDeviceService = service;
        mCarTrustAgentBleManager = bleService;
    }

    /**
     * The interface that an unlock delegate has to implement to get the auth credentials from
     * the unlock service.
     */
    interface CarTrustAgentUnlockDelegate {
        /**
         * Called when the Unlock service has the auth credentials to pass.
         *
         * @param user   user being authorized
         * @param token  escrow token for the user
         * @param handle the handle corresponding to the escrow token
         */
        void onUnlockDataReceived(int user, byte[] token, long handle);
    }

    /**
     * Enable or disable authentication of the head unit with a trusted device.
     *
     * @param isEnabled when set to {@code false}, head unit will not be
     *                  discoverable to unlock the user. Setting it to {@code true} will enable it
     *                  back.
     */
    public void setTrustedDeviceUnlockEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mTrustedDeviceService.getSharedPrefs().edit();
        editor.putBoolean(TRUSTED_DEVICE_UNLOCK_ENABLED_KEY, isEnabled);
        if (!editor.commit()) {
            Log.wtf(TAG, "Unlock Enable Failed. Enable? " + isEnabled);
        }
    }

    /**
     * Set a delegate that implements {@link CarTrustAgentUnlockDelegate}. The delegate will be
     * handed the auth related data (token and handle) when it is received from the remote
     * trusted device. The delegate is expected to use that to authorize the user.
     */
    void setUnlockRequestDelegate(CarTrustAgentUnlockDelegate delegate) {
        mUnlockDelegate = delegate;
    }

    /**
     * Start Unlock Advertising
     */
    void startUnlockAdvertising() {
        if (!mTrustedDeviceService.getSharedPrefs().getBoolean(TRUSTED_DEVICE_UNLOCK_ENABLED_KEY,
                true)) {
            Log.e(TAG, "Trusted Device Unlock is disabled");
            return;
        }
        mTrustedDeviceService.getCarTrustAgentEnrollmentService().stopEnrollmentAdvertising();
        stopUnlockAdvertising();

        logUnlockEvent(START_UNLOCK_ADVERTISING);
        queueMessageForLog("startUnlockAdvertising");
        mCarTrustAgentBleManager.startUnlockAdvertising();
    }

    /**
     * Stop unlock advertising
     */
    void stopUnlockAdvertising() {
        logUnlockEvent(STOP_UNLOCK_ADVERTISING);
        queueMessageForLog("stopUnlockAdvertising");
        mCarTrustAgentBleManager.stopUnlockAdvertising();
        // Also disconnect from the peer.
        if (mRemoteUnlockDevice != null) {
            mCarTrustAgentBleManager.disconnectRemoteDevice();
            mRemoteUnlockDevice = null;
        }
    }

    void init() {
        logUnlockEvent(UNLOCK_SERVICE_INIT);
        mCarTrustAgentBleManager.setupUnlockBleServer();
    }

    void release() {
        synchronized (mDeviceLock) {
            mRemoteUnlockDevice = null;
        }
        mPrevContext = null;
        mCurrentContext = null;
    }

    void onRemoteDeviceConnected(BluetoothDevice device) {
        synchronized (mDeviceLock) {
            if (mRemoteUnlockDevice != null) {
                // TBD, return when this is encountered?
                Log.e(TAG, "Unexpected: Cannot connect to another device when already connected");
            }
            queueMessageForLog("onRemoteDeviceConnected (addr:" + device.getAddress() + ")");
            logUnlockEvent(REMOTE_DEVICE_CONNECTED);
            mRemoteUnlockDevice = device;
        }
        resetEncryptionState();
        mCurrentUnlockState = UNLOCK_STATE_WAITING_FOR_UNIQUE_ID;
    }

    void onRemoteDeviceDisconnected(BluetoothDevice device) {
        // sanity checking
        if (!device.equals(mRemoteUnlockDevice) && device.getAddress() != null) {
            Log.e(TAG, "Disconnected from an unknown device:" + device.getAddress());
        }
        queueMessageForLog("onRemoteDeviceDisconnected (addr:" + device.getAddress() + ")");
        synchronized (mDeviceLock) {
            mRemoteUnlockDevice = null;
        }
        resetEncryptionState();
        mCurrentUnlockState = UNLOCK_STATE_WAITING_FOR_UNIQUE_ID;
    }

    void onUnlockDataReceived(byte[] value) {
        switch (mCurrentUnlockState) {
            case UNLOCK_STATE_WAITING_FOR_UNIQUE_ID:
                if (!CarTrustAgentValidator.isValidUnlockDeviceId(value)) {
                    Log.e(TAG, "Device Id rejected by validator.");
                    resetUnlockStateOnFailure();
                    return;
                }
                mClientDeviceId = convertToDeviceId(value);
                if (mClientDeviceId == null) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Phone not enrolled as a trusted device");
                    }
                    resetUnlockStateOnFailure();
                    return;
                }
                logUnlockEvent(RECEIVED_DEVICE_ID);
                sendAckToClient(/* isEncrypted = */ false);
                // Next step is to wait for the client to start the encryption handshake.
                mCurrentUnlockState = UNLOCK_STATE_KEY_EXCHANGE_IN_PROGRESS;
                break;
            case UNLOCK_STATE_KEY_EXCHANGE_IN_PROGRESS:
                try {
                    processKeyExchangeHandshakeMessage(value);
                } catch (HandshakeException e) {
                    Log.e(TAG, "Handshake failure", e);
                    resetUnlockStateOnFailure();
                }
                break;
            case UNLOCK_STATE_WAITING_FOR_CLIENT_AUTH:
                if (!authenticateClient(value)) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "HMAC from the phone is not correct. Cannot resume session. Need"
                                + " to re-enroll");
                    }
                    mTrustedDeviceService.clearEncryptionKey(mClientDeviceId);
                    resetUnlockStateOnFailure();
                    return;
                }

                logUnlockEvent(CLIENT_AUTHENTICATED);
                sendServerAuthToClient();
                mCurrentUnlockState = UNLOCK_STATE_MUTUAL_AUTH_ESTABLISHED;
                break;
            case UNLOCK_STATE_MUTUAL_AUTH_ESTABLISHED:
                if (mEncryptionKey == null) {
                    Log.e(TAG, "Current session key null. Unexpected at this stage: "
                            + mCurrentUnlockState);
                    // Clear the previous session key.  Need to re-enroll the trusted device.
                    mTrustedDeviceService.clearEncryptionKey(mClientDeviceId);
                    resetUnlockStateOnFailure();
                    return;
                }

                // Save the current session to be used for authenticating the next session
                mTrustedDeviceService.saveEncryptionKey(mClientDeviceId, mEncryptionKey.asBytes());

                byte[] decryptedCredentials;
                try {
                    decryptedCredentials = mEncryptionKey.decryptData(value);
                } catch (SignatureException e) {
                    Log.e(TAG, "Could not decrypt phone credentials.", e);
                    resetUnlockStateOnFailure();
                    return;
                }

                processCredentials(decryptedCredentials);
                mCurrentUnlockState = UNLOCK_STATE_PHONE_CREDENTIALS_RECEIVED;
                logUnlockEvent(UNLOCK_CREDENTIALS_RECEIVED);

                // Let the phone know that the token was received.
                sendAckToClient(/* isEncrypted = */ true);
                break;
            case UNLOCK_STATE_PHONE_CREDENTIALS_RECEIVED:
                // Should never get here because the unlock process should be completed now.
                Log.e(TAG, "Landed on unexpected state of credentials received.");
                break;
            default:
                Log.e(TAG, "Encountered unexpected unlock state: " + mCurrentUnlockState);
        }
    }

    private void sendAckToClient(boolean isEncrypted) {
        // Let the phone know that the handle was received.
        byte[] ack = isEncrypted ? mEncryptionKey.encryptData(ACKNOWLEDGEMENT_MESSAGE)
                : ACKNOWLEDGEMENT_MESSAGE;
        mCarTrustAgentBleManager.sendUnlockMessage(mRemoteUnlockDevice, ack,
                OperationType.CLIENT_MESSAGE, /* isPayloadEncrypted= */ isEncrypted);
    }

    @Nullable
    private String convertToDeviceId(byte[] id) {
        // Validate if the id exists i.e., if the phone is enrolled already
        UUID deviceId = Utils.bytesToUUID(id);
        if (deviceId == null
                || mTrustedDeviceService.getEncryptionKey(deviceId.toString()) == null) {
            if (deviceId != null) {
                Log.e(TAG, "Unknown phone connected: " + deviceId.toString());
            }
            return null;
        }

        return deviceId.toString();
    }

    private void processKeyExchangeHandshakeMessage(byte[] message) throws HandshakeException {
        switch (mEncryptionState) {
            case HandshakeMessage.HandshakeState.UNKNOWN:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Responding to handshake init request.");
                }

                mHandshakeMessage = mEncryptionRunner.respondToInitRequest(message);
                mEncryptionState = mHandshakeMessage.getHandshakeState();
                mCarTrustAgentBleManager.sendUnlockMessage(mRemoteUnlockDevice,
                        mHandshakeMessage.getNextMessage(),
                        OperationType.ENCRYPTION_HANDSHAKE,
                        /* isPayloadEncrypted= */ false);
                logUnlockEvent(UNLOCK_ENCRYPTION_STATE, mEncryptionState);
                break;

            case HandshakeMessage.HandshakeState.IN_PROGRESS:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Continuing handshake.");
                }

                mHandshakeMessage = mEncryptionRunner.continueHandshake(message);
                mEncryptionState = mHandshakeMessage.getHandshakeState();

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Updated encryption state: " + mEncryptionState);
                }

                // The state is updated after a call to continueHandshake(). Thus, need to check
                // if we're in the next stage.
                if (mEncryptionState == HandshakeMessage.HandshakeState.VERIFICATION_NEEDED) {
                    logUnlockEvent(UNLOCK_ENCRYPTION_STATE, mEncryptionState);
                    showVerificationCode();
                    return;
                }

                // control shouldn't get here with Ukey2
                mCarTrustAgentBleManager.sendUnlockMessage(mRemoteUnlockDevice,
                        mHandshakeMessage.getNextMessage(),
                        OperationType.ENCRYPTION_HANDSHAKE, /*isPayloadEncrypted= */false);
                break;
            case HandshakeMessage.HandshakeState.VERIFICATION_NEEDED:
            case HandshakeMessage.HandshakeState.FINISHED:
                // Should never reach this case since this state should occur after a verification
                // code has been accepted. But it should mean handshake is done and the message
                // is one for the escrow token. Start Mutual Auth from server - compute MACs and
                // send it over
                showVerificationCode();
                break;

            default:
                Log.w(TAG, "Encountered invalid handshake state: " + mEncryptionState);
                break;
        }
    }

    /**
     * Verify the handshake.
     * TODO(b/134073741) combine this with the method in CarTrustAgentEnrollmentService and
     * have this take a boolean to blindly confirm the numeric code.
     */
    private void showVerificationCode() {
        HandshakeMessage handshakeMessage;

        // Blindly accept the verification code.
        try {
            handshakeMessage = mEncryptionRunner.verifyPin();
        } catch (HandshakeException e) {
            Log.e(TAG, "Verify pin failed for new keys - Unexpected");
            resetUnlockStateOnFailure();
            return;
        }

        if (handshakeMessage.getHandshakeState() != HandshakeMessage.HandshakeState.FINISHED) {
            Log.e(TAG, "Handshake not finished after calling verify PIN. Instead got state: "
                    + handshakeMessage.getHandshakeState());
            resetUnlockStateOnFailure();
            return;
        }

        mEncryptionState = HandshakeMessage.HandshakeState.FINISHED;
        mEncryptionKey = handshakeMessage.getKey();
        mCurrentContext = D2DConnectionContext.fromSavedSession(mEncryptionKey.asBytes());

        if (mClientDeviceId == null) {
            resetUnlockStateOnFailure();
            return;
        }
        byte[] oldSessionKeyBytes = mTrustedDeviceService.getEncryptionKey(mClientDeviceId);
        if (oldSessionKeyBytes == null) {
            Log.e(TAG,
                    "Could not retrieve previous session keys! Have to re-enroll trusted device");
            resetUnlockStateOnFailure();
            return;
        }

        mPrevContext = D2DConnectionContext.fromSavedSession(oldSessionKeyBytes);
        if (mPrevContext == null) {
            resetUnlockStateOnFailure();
            return;
        }

        // Now wait for the phone to send its MAC.
        mCurrentUnlockState = UNLOCK_STATE_WAITING_FOR_CLIENT_AUTH;
        logUnlockEvent(WAITING_FOR_CLIENT_AUTH);
    }

    private void sendServerAuthToClient() {
        byte[] resumeBytes = computeMAC(mPrevContext, mCurrentContext, SERVER);
        if (resumeBytes == null) {
            return;
        }
        // send to client
        mCarTrustAgentBleManager.sendUnlockMessage(mRemoteUnlockDevice, resumeBytes,
                OperationType.CLIENT_MESSAGE, /* isPayloadEncrypted= */false);
    }

    @Nullable
    private byte[] computeMAC(D2DConnectionContext previous, D2DConnectionContext next,
            byte[] info) {
        try {
            SecretKeySpec inputKeyMaterial = new SecretKeySpec(
                    Utils.concatByteArrays(previous.getSessionUnique(), next.getSessionUnique()),
                    "" /* key type is just plain raw bytes */);
            return CryptoOps.hkdf(inputKeyMaterial, RESUME, info);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Does not happen in practice
            Log.e(TAG, "Compute MAC failed");
            return null;
        }
    }

    private boolean authenticateClient(byte[] message) {
        if (message.length != RESUME_HMAC_LENGTH) {
            Log.e(TAG, "failing because message.length is " + message.length);
            return false;
        }
        return MessageDigest.isEqual(message,
                computeMAC(mPrevContext, mCurrentContext, CLIENT));
    }

    void processCredentials(byte[] credentials) {
        if (mUnlockDelegate == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No Unlock delegate to notify of unlock credentials.");
            }
            return;
        }

        queueMessageForLog("processCredentials");

        PhoneCredentials phoneCredentials;
        try {
            phoneCredentials = PhoneCredentials.parseFrom(credentials);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Error parsing credentials protobuf.", e);
            return;
        }

        byte[] handle = phoneCredentials.getHandle().toByteArray();

        mUnlockDelegate.onUnlockDataReceived(
                mTrustedDeviceService.getUserHandleByTokenHandle(Utils.bytesToLong(handle)),
                phoneCredentials.getEscrowToken().toByteArray(),
                Utils.bytesToLong(handle));
    }

    /**
     * Reset the whole unlock state.  Disconnects from the peer device
     *
     * <p>This method should be called from any stage in the middle of unlock where we
     * encounter a failure.
     */
    private void resetUnlockStateOnFailure() {
        mCarTrustAgentBleManager.disconnectRemoteDevice();
        resetEncryptionState();
    }

    /**
     * Resets the encryption status of this service.
     *
     * <p>This method should be called each time a device connects so that a new handshake can be
     * started and encryption keys exchanged.
     */
    private void resetEncryptionState() {
        mEncryptionRunner = EncryptionRunnerFactory.newRunner();
        mHandshakeMessage = null;
        mEncryptionKey = null;
        mEncryptionState = HandshakeMessage.HandshakeState.UNKNOWN;
        mCurrentUnlockState = UNLOCK_STATE_WAITING_FOR_UNIQUE_ID;
        if (mCurrentContext != null) {
            mCurrentContext = null;
        }
        if (mPrevContext != null) {
            mPrevContext = null;
        }
    }

    void dump(PrintWriter writer) {
        writer.println("*CarTrustAgentUnlockService*");
        writer.println("Unlock Service Logs:");
        for (String log : mLogQueue) {
            writer.println("\t" + log);
        }
    }

    private void queueMessageForLog(String message) {
        if (mLogQueue.size() >= MAX_LOG_SIZE) {
            mLogQueue.remove();
        }
        mLogQueue.add(System.currentTimeMillis() + " : " + message);
    }
}
