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

package com.android.car;

import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_PROFILES_INHIBITED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the inhibiting of Bluetooth profile connections to and from specific devices.
 */
public class BluetoothProfileInhibitManager {
    private static final String TAG = "BluetoothProfileInhibitManager";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String SETTINGS_DELIMITER = ",";
    private static final Binder RESTORED_PROFILE_INHIBIT_TOKEN = new Binder();
    private static final long RESTORE_BACKOFF_MILLIS = 1000L;

    private final Context mContext;

    // Per-User information
    private final int mUserId;
    private final ICarBluetoothUserService mBluetoothUserProxies;

    @GuardedBy("this")
    private final SetMultimap<BluetoothConnection, InhibitRecord> mProfileInhibits =
            new SetMultimap<>();

    @GuardedBy("this")
    private final HashSet<InhibitRecord> mRestoredInhibits = new HashSet<>();

    @GuardedBy("this")
    private final HashSet<BluetoothConnection> mAlreadyDisabledProfiles = new HashSet<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * BluetoothConnection - encapsulates the information representing a connection to a device on a
     * given profile. This object is hashable, encodable and decodable.
     *
     * Encodes to the following structure:
     * <device>/<profile>
     *
     * Where,
     *    device - the device we're connecting to, can be null
     *    profile - the profile we're connecting on, can be null
     */
    public static class BluetoothConnection {
        // Examples:
        // 01:23:45:67:89:AB/9
        // null/0
        // null/null
        private static final String FLATTENED_PATTERN =
                "^(([0-9A-F]{2}:){5}[0-9A-F]{2}|null)/([0-9]+|null)$";

        private final BluetoothDevice mBluetoothDevice;
        private final Integer mBluetoothProfile;

        public BluetoothConnection(Integer profile, BluetoothDevice device) {
            mBluetoothProfile = profile;
            mBluetoothDevice = device;
        }

        public BluetoothDevice getDevice() {
            return mBluetoothDevice;
        }

        public Integer getProfile() {
            return mBluetoothProfile;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BluetoothConnection)) {
                return false;
            }
            BluetoothConnection otherParams = (BluetoothConnection) other;
            return Objects.equals(mBluetoothDevice, otherParams.mBluetoothDevice)
                && Objects.equals(mBluetoothProfile, otherParams.mBluetoothProfile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mBluetoothDevice, mBluetoothProfile);
        }

        @Override
        public String toString() {
            return encode();
        }

        /**
         * Converts these {@link BluetoothConnection} to a parseable string representation.
         *
         * @return A parseable string representation of this BluetoothConnection object.
         */
        public String encode() {
            return mBluetoothDevice + "/" + mBluetoothProfile;
        }

        /**
         * Creates a {@link BluetoothConnection} from a previous output of {@link #encode()}.
         *
         * @param flattenedParams A flattened string representation of a {@link BluetoothConnection}
         */
        public static BluetoothConnection decode(String flattenedParams) {
            if (!flattenedParams.matches(FLATTENED_PATTERN)) {
                throw new IllegalArgumentException("Bad format for flattened BluetoothConnection");
            }

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                return new BluetoothConnection(null, null);
            }

            String[] parts = flattenedParams.split("/");

            BluetoothDevice device;
            if (!"null".equals(parts[0])) {
                device = adapter.getRemoteDevice(parts[0]);
            } else {
                device = null;
            }

            Integer profile;
            if (!"null".equals(parts[1])) {
                profile = Integer.valueOf(parts[1]);
            } else {
                profile = null;
            }

            return new BluetoothConnection(profile, device);
        }
    }

    private class InhibitRecord implements IBinder.DeathRecipient {
        private final BluetoothConnection mParams;
        private final IBinder mToken;

        private boolean mRemoved = false;

        InhibitRecord(BluetoothConnection params, IBinder token) {
            this.mParams = params;
            this.mToken = token;
        }

        public BluetoothConnection getParams() {
            return mParams;
        }

        public IBinder getToken() {
            return mToken;
        }

        public boolean removeSelf() {
            synchronized (BluetoothProfileInhibitManager.this) {
                if (mRemoved) {
                    return true;
                }

                if (removeInhibitRecord(this)) {
                    mRemoved = true;
                    return true;
                } else {
                    return false;
                }
            }
        }

        @Override
        public void binderDied() {
            logd("Releasing inhibit request on profile "
                    + Utils.getProfileName(mParams.getProfile())
                    + " for device " + mParams.getDevice()
                    + ": requesting process died");
            removeSelf();
        }
    }

    /**
     * Creates a new instance of a BluetoothProfileInhibitManager
     *
     * @param context - context of calling code
     * @param userId - ID of user we want to manage inhibits for
     * @param bluetoothUserProxies - Set of per-user bluetooth proxies for calling into the
     *                               bluetooth stack as the current user.
     * @return A new instance of a BluetoothProfileInhibitManager
     */
    public BluetoothProfileInhibitManager(Context context, int userId,
            ICarBluetoothUserService bluetoothUserProxies) {
        mContext = context;
        mUserId = userId;
        mBluetoothUserProxies = bluetoothUserProxies;
    }

    /**
     * Create {@link InhibitRecord}s for all profile inhibits written to {@link Settings.Secure}.
     */
    private void load() {
        String savedBluetoothConnection = Settings.Secure.getStringForUser(
                mContext.getContentResolver(), KEY_BLUETOOTH_PROFILES_INHIBITED, mUserId);

        if (TextUtils.isEmpty(savedBluetoothConnection)) {
            return;
        }

        logd("Restoring profile inhibits: " + savedBluetoothConnection);

        for (String paramsStr : savedBluetoothConnection.split(SETTINGS_DELIMITER)) {
            try {
                BluetoothConnection params = BluetoothConnection.decode(paramsStr);
                InhibitRecord record = new InhibitRecord(params, RESTORED_PROFILE_INHIBIT_TOKEN);
                mProfileInhibits.put(params, record);
                mRestoredInhibits.add(record);
                logd("Restored profile inhibits for " + params);
            } catch (IllegalArgumentException e) {
                // We won't ever be able to fix a bad parse, so skip it and move on.
                loge("Bad format for saved profile inhibit: " + paramsStr + ", " + e);
            }
        }
    }

    /**
     * Dump all currently-active profile inhibits to {@link Settings.Secure}.
     */
    private void commit() {
        Set<BluetoothConnection> inhibitedProfiles = new HashSet<>(mProfileInhibits.keySet());
        // Don't write out profiles that were disabled before a request was made, since
        // restoring those profiles is a no-op.
        inhibitedProfiles.removeAll(mAlreadyDisabledProfiles);
        String savedDisconnects =
                inhibitedProfiles
                        .stream()
                        .map(BluetoothConnection::encode)
                        .collect(Collectors.joining(SETTINGS_DELIMITER));

        Settings.Secure.putStringForUser(
                mContext.getContentResolver(), KEY_BLUETOOTH_PROFILES_INHIBITED,
                savedDisconnects, mUserId);

        logd("Committed key: " + KEY_BLUETOOTH_PROFILES_INHIBITED + ", value: '"
                + savedDisconnects + "'");
    }

    /**
     *
     */
    public void start() {
        load();
        removeRestoredProfileInhibits();
    }

    /**
     *
     */
    public void stop() {
        releaseAllInhibitsBeforeUnbind();
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @param token   A {@link IBinder} to be used as an identity for the request. If the process
     *                owning the token dies, the request will automatically be released
     * @return True if the profile was successfully inhibited, false if an error occurred.
     */
    boolean requestProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Request profile inhibit: profile " + Utils.getProfileName(profile)
                + ", device " + device.getAddress());
        BluetoothConnection params = new BluetoothConnection(profile, device);
        InhibitRecord record = new InhibitRecord(params, token);
        return addInhibitRecord(record);
    }

    /**
     * Undo a previous call to {@link #requestProfileInhibit} with the same parameters,
     * and reconnect the profile if no other requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return True if the request was released, false if an error occurred.
     */
    boolean releaseProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Release profile inhibit: profile " + Utils.getProfileName(profile)
                + ", device " + device.getAddress());

        BluetoothConnection params = new BluetoothConnection(profile, device);
        InhibitRecord record;
        synchronized (this) {
            record = findInhibitRecord(params, token);
        }

        if (record == null) {
            Log.e(TAG, "Record not found");
            return false;
        }

        return record.removeSelf();
    }

    /**
     * Add a profile inhibit record, disabling the profile if necessary.
     */
    private synchronized boolean addInhibitRecord(InhibitRecord record) {
        BluetoothConnection params = record.getParams();
        if (!isProxyAvailable(params.getProfile())) {
            return false;
        }

        Set<InhibitRecord> previousRecords = mProfileInhibits.get(params);
        if (findInhibitRecord(params, record.getToken()) != null) {
            Log.e(TAG, "Inhibit request already registered - skipping duplicate");
            return false;
        }

        try {
            record.getToken().linkToDeath(record, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not link to death on inhibit token (already dead?)", e);
            return false;
        }

        boolean isNewlyAdded = previousRecords.isEmpty();
        mProfileInhibits.put(params, record);

        if (isNewlyAdded) {
            try {
                int priority =
                        mBluetoothUserProxies.getProfilePriority(
                                params.getProfile(),
                                params.getDevice());
                if (priority == BluetoothProfile.PRIORITY_OFF) {
                    // This profile was already disabled (and not as the result of an inhibit).
                    // Add it to the already-disabled list, and do nothing else.
                    mAlreadyDisabledProfiles.add(params);

                    logd("Profile " + Utils.getProfileName(params.getProfile())
                            + " already disabled for device " + params.getDevice()
                            + " - suppressing re-enable");
                } else {
                    mBluetoothUserProxies.setProfilePriority(
                            params.getProfile(),
                            params.getDevice(),
                            BluetoothProfile.PRIORITY_OFF);
                    mBluetoothUserProxies.bluetoothDisconnectFromProfile(
                            params.getProfile(),
                            params.getDevice());
                    logd("Disabled profile "
                            + Utils.getProfileName(params.getProfile())
                            + " for device " + params.getDevice());
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Could not disable profile", e);
                record.getToken().unlinkToDeath(record, 0);
                mProfileInhibits.remove(params, record);
                return false;
            }
        }

        commit();
        return true;
    }

    /**
     * Find the inhibit record, if any, corresponding to the given parameters and token.
     *
     * @param params  BluetoothConnection parameter pair that could have an inhibit on it
     * @param token   The token provided in the call to {@link #requestBluetoothProfileInhibit}.
     * @return InhibitRecord for the connection parameters and token if exists, null otherwise.
     */
    private InhibitRecord findInhibitRecord(BluetoothConnection params, IBinder token) {
        return mProfileInhibits.get(params)
            .stream()
            .filter(r -> r.getToken() == token)
            .findAny()
            .orElse(null);
    }

    /**
     * Remove a given profile inhibit record, reconnecting if necessary.
     */
    private synchronized boolean removeInhibitRecord(InhibitRecord record) {
        BluetoothConnection params = record.getParams();
        if (!isProxyAvailable(params.getProfile())) {
            return false;
        }
        if (!mProfileInhibits.containsEntry(params, record)) {
            Log.e(TAG, "Record already removed");
            // Removing something a second time vacuously succeeds.
            return true;
        }

        // Re-enable profile before unlinking and removing the record, in case of error.
        // The profile should be re-enabled if this record is the only one left for that
        // device and profile combination.
        if (mProfileInhibits.get(params).size() == 1) {
            if (!restoreProfilePriority(params)) {
                return false;
            }
        }

        record.getToken().unlinkToDeath(record, 0);
        mProfileInhibits.remove(params, record);

        commit();
        return true;
    }

    /**
     * Re-enable and reconnect a given profile for a device.
     */
    private boolean restoreProfilePriority(BluetoothConnection params) {
        if (!isProxyAvailable(params.getProfile())) {
            return false;
        }

        if (mAlreadyDisabledProfiles.remove(params)) {
            // The profile does not need any state changes, since it was disabled
            // before it was inhibited. Leave it disabled.
            logd("Not restoring profile "
                    + Utils.getProfileName(params.getProfile()) + " for device "
                    + params.getDevice() + " - was manually disabled");
            return true;
        }

        try {
            mBluetoothUserProxies.setProfilePriority(
                    params.getProfile(),
                    params.getDevice(),
                    BluetoothProfile.PRIORITY_ON);
            mBluetoothUserProxies.bluetoothConnectToProfile(
                    params.getProfile(),
                    params.getDevice());
            logd("Restored profile " + Utils.getProfileName(params.getProfile())
                    + " for device " + params.getDevice());
            return true;
        } catch (RemoteException e) {
            loge("Could not enable profile: " + e);
            return false;
        }
    }

    /**
     * Try once to remove all restored profile inhibits.
     *
     * If the CarBluetoothUserService is not yet available, or it hasn't yet bound its profile
     * proxies, the removal will fail, and will need to be retried later.
     */
    private void tryRemoveRestoredProfileInhibits() {
        HashSet<InhibitRecord> successfullyRemoved = new HashSet<>();

        for (InhibitRecord record : mRestoredInhibits) {
            if (removeInhibitRecord(record)) {
                successfullyRemoved.add(record);
            }
        }

        mRestoredInhibits.removeAll(successfullyRemoved);
    }

    /**
     * Keep trying to remove all profile inhibits that were restored from settings
     * until all such inhibits have been removed.
     */
    private synchronized void removeRestoredProfileInhibits() {
        tryRemoveRestoredProfileInhibits();

        if (!mRestoredInhibits.isEmpty()) {
            logd("Could not remove all restored profile inhibits - "
                        + "trying again in " + RESTORE_BACKOFF_MILLIS + "ms");
            mHandler.postDelayed(
                    this::removeRestoredProfileInhibits,
                    RESTORED_PROFILE_INHIBIT_TOKEN,
                    RESTORE_BACKOFF_MILLIS);
        }
    }

    /**
     * Release all active inhibit records prior to user switch or shutdown
     */
    private synchronized void releaseAllInhibitsBeforeUnbind() {
        logd("Unbinding CarBluetoothUserService - releasing all profile inhibits");
        for (BluetoothConnection params : mProfileInhibits.keySet()) {
            for (InhibitRecord record : mProfileInhibits.get(params)) {
                record.removeSelf();
            }
        }

        // Some inhibits might be hanging around because they couldn't be cleaned up.
        // Make sure they get persisted...
        commit();

        // ...then clear them from the map.
        mProfileInhibits.clear();

        // We don't need to maintain previously-disabled profiles any more - they were already
        // skipped in saveProfileInhibitsToSettings() above, and they don't need any
        // further handling when the user resumes.
        mAlreadyDisabledProfiles.clear();

        // Clean up bookkeeping for restored inhibits. (If any are still around, they'll be
        // restored again when this user restarts.)
        mHandler.removeCallbacksAndMessages(RESTORED_PROFILE_INHIBIT_TOKEN);
        mRestoredInhibits.clear();
    }

    /**
     * Determines if the per-user bluetooth proxy for a given profile is active and usable.
     *
     * @return True if proxy is available, false otherwise
     */
    private boolean isProxyAvailable(int profile) {
        try {
            return mBluetoothUserProxies.isBluetoothConnectionProxyAvailable(profile);
        } catch (RemoteException e) {
            loge("Car BT Service Remote Exception. Proxy for " + Utils.getProfileName(profile)
                    + " not available.");
        }
        return false;
    }

    /**
     * Print the verbose status of the object
     */
    public synchronized void dump(PrintWriter writer, String indent) {
        writer.println(indent + TAG + ":");

        // User metadata
        writer.println(indent + "\tUser: " + mUserId);

        // Current inhibits
        String inhibits;
        synchronized (this) {
            inhibits = mProfileInhibits.keySet().toString();
        }
        writer.println(indent + "\tInhibited profiles: " + inhibits);
    }

    /**
     * Log a message to Log.DEBUG
     */
    private void logd(String msg) {
        if (DBG) {
            Log.d(TAG, "[User: " + mUserId + "] " + msg);
        }
    }

    /**
     * Log a message to Log.WARN
     */
    private void logw(String msg) {
        Log.w(TAG, "[User: " + mUserId + "] " + msg);
    }

    /**
     * Log a message to Log.ERROR
     */
    private void loge(String msg) {
        Log.e(TAG, "[User: " + mUserId + "] " + msg);
    }
}
