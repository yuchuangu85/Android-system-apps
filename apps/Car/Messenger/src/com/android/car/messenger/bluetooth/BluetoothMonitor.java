package com.android.car.messenger.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.SdpMasRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.messenger.R;
import com.android.car.messenger.log.L;

import java.util.HashSet;
import java.util.Set;


/**
 * Provides a callback interface for subscribers to be notified of bluetooth MAP/SDP changes.
 */
public class BluetoothMonitor {
    private static final String TAG = "CM.BluetoothMonitor";

    private final Context mContext;
    private final BluetoothMapReceiver mBluetoothMapReceiver;
    private final BluetoothSdpReceiver mBluetoothSdpReceiver;
    private final MapDeviceMonitor mMapDeviceMonitor;
    private final BluetoothProfile.ServiceListener mMapServiceListener;

    private final Set<OnBluetoothEventListener> mListeners;

    public BluetoothMonitor(@NonNull Context context) {
        mContext = context;

        mBluetoothMapReceiver = new BluetoothMapReceiver();
        mBluetoothSdpReceiver = new BluetoothSdpReceiver();
        mMapDeviceMonitor = new MapDeviceMonitor();
        mMapServiceListener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                L.d(TAG, "Connected to MAP service!");
                onMapConnected((BluetoothMapClient) proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
                L.d(TAG, "Disconnected from MAP service!");
                onMapDisconnected(profile);
            }
        };
        mListeners = new HashSet<>();
        connectToMap();
    }

    /**
     * Registers a listener to receive Bluetooth MAP events.
     * If this listener is already registered, calling this method has no effect.
     *
     * @param listener the listener to register
     * @return true if this listener was not already registered
     */
    public boolean registerListener(@NonNull OnBluetoothEventListener listener) {
        return mListeners.add(listener);
    }

    /**
     * Unregisters a listener from receiving Bluetooth MAP events.
     * If this listener is not registered, calling this method has no effect.
     *
     * @param listener the listener to unregister
     * @return true if the set of registered listeners contained this listener
     */
    public boolean unregisterListener(OnBluetoothEventListener listener) {
        return mListeners.remove(listener);
    }

    public interface OnBluetoothEventListener {
        /**
         * Callback issued when a new message was received.
         *
         * @param intent intent containing the message details
         */
        void onMessageReceived(Intent intent);

        /**
         * Callback issued when a new message was sent successfully.
         *
         * @param intent intent containing the message details
         */
        void onMessageSent(Intent intent);

        /**
         * Callback issued when a new device has connected to bluetooth.
         *
         * @param device the connected device
         */
        void onDeviceConnected(BluetoothDevice device);

        /**
         * Callback issued when a previously connected device has disconnected from bluetooth.
         *
         * @param device the disconnected device
         */
        void onDeviceDisconnected(BluetoothDevice device);

        /**
         * Callback issued when a new MAP client has been connected.
         *
         * @param client the MAP client
         */
        void onMapConnected(BluetoothMapClient client);

        /**
         * Callback issued when a MAP client has been disconnected.
         *
         * @param profile see {@link BluetoothProfile.ServiceListener#onServiceDisconnected(int)}
         */
        void onMapDisconnected(int profile);

        /**
         * Callback issued when a new SDP record has been detected.
         *
         * @param device        the device detected
         * @param supportsReply true if the device supports SMS replies through bluetooth
         */
        void onSdpRecord(BluetoothDevice device, boolean supportsReply);
    }

    private void onMessageReceived(Intent intent) {
        mListeners.forEach(listener -> listener.onMessageReceived(intent));
    }

    private void onMessageSent(Intent intent) {
        mListeners.forEach(listener -> listener.onMessageSent(intent));
    }

    private void onDeviceConnected(BluetoothDevice device) {
        mListeners.forEach(listener -> listener.onDeviceConnected(device));
    }

    private void onDeviceDisconnected(BluetoothDevice device) {
        mListeners.forEach(listener -> listener.onDeviceDisconnected(device));
    }

    private void onMapConnected(BluetoothMapClient client) {
        mListeners.forEach(listener -> listener.onMapConnected(client));
    }

    private void onMapDisconnected(int profile) {
        mListeners.forEach(listener -> listener.onMapDisconnected(profile));
        boolean shouldReconnectToMap = false;
        try {
            shouldReconnectToMap = mContext.getResources().getBoolean(
                    R.bool.config_loadExistingMessages);
        } catch (NotFoundException e) {
            // Should only happen for robolectric unit tests
            L.e(TAG, e, "Could not find loadExistingMessages config");
        }
        if (shouldReconnectToMap) {
            connectToMap();
        }
    }

    private void onSdpRecord(BluetoothDevice device, boolean supportsReply) {
        mListeners.forEach(listener -> listener.onSdpRecord(device, supportsReply));
    }

    /** Connects to the MAP client. */
    private void connectToMap() {
        L.d(TAG, "Connecting to MAP service");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            // This can happen on devices that don't support Bluetooth.
            L.e(TAG, "BluetoothAdapter is null! Unable to connect to MAP client.");
            return;
        }

        if (!adapter.getProfileProxy(mContext, mMapServiceListener, BluetoothProfile.MAP_CLIENT)) {
            // This *should* never happen.  Unless arguments passed are incorrect somehow...
            L.wtf(TAG, "Unable to get MAP profile!");
            return;
        }
    }

    /**
     * Performs {@link Context} related cleanup (such as unregistering from receivers).
     */
    public void cleanup() {
        mListeners.clear();
        mBluetoothMapReceiver.unregisterReceivers();
        mBluetoothSdpReceiver.unregisterReceivers();
        mMapDeviceMonitor.unregisterReceivers();
    }

    @VisibleForTesting
    BluetoothProfile.ServiceListener getServiceListener() {
        return mMapServiceListener;
    }

    /** Monitors for new device connections and disconnections */
    private class MapDeviceMonitor extends BroadcastReceiver {
        MapDeviceMonitor() {
            L.d(TAG, "Registering Map device monitor");

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED);
            mContext.registerReceiver(this, intentFilter,
                    android.Manifest.permission.BLUETOOTH, null);
        }

        void unregisterReceivers() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final int STATE_NOT_FOUND = -1;
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, STATE_NOT_FOUND);
            int previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                    STATE_NOT_FOUND);

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (state == STATE_NOT_FOUND || previousState == STATE_NOT_FOUND || device == null) {
                L.w(TAG, "Skipping broadcast, missing required extra");
                return;
            }

            if (previousState == BluetoothProfile.STATE_CONNECTED
                    && state != BluetoothProfile.STATE_CONNECTED) {
                L.d(TAG, "Device losing MAP connection: %s", device);

                onDeviceDisconnected(device);
            }

            if (previousState == BluetoothProfile.STATE_CONNECTING
                    && state == BluetoothProfile.STATE_CONNECTED) {
                L.d(TAG, "Device connected: %s", device);

                onDeviceConnected(device);
            }
        }
    }

    /** Monitors for new incoming messages and sent-message broadcast. */
    private class BluetoothMapReceiver extends BroadcastReceiver {
        BluetoothMapReceiver() {
            L.d(TAG, "Registering receiver for bluetooth MAP");

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY);
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_RECEIVED);
            mContext.registerReceiver(this, intentFilter);
        }

        void unregisterReceivers() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null || intent.getAction() == null) return;

            switch (intent.getAction()) {
                case BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY:
                    L.d(TAG, "SMS sent successfully.");
                    onMessageSent(intent);
                    break;
                case BluetoothMapClient.ACTION_MESSAGE_RECEIVED:
                    L.d(TAG, "SMS message received.");
                    onMessageReceived(intent);
                    break;
                default:
                    L.w(TAG, "Ignoring unknown broadcast %s", intent.getAction());
                    break;
            }
        }
    }

    /** Monitors for new SDP records */
    private class BluetoothSdpReceiver extends BroadcastReceiver {

        // reply or "upload" feature is indicated by the 3rd bit
        private static final int REPLY_FEATURE_FLAG_POSITION = 3;
        private static final int REPLY_FEATURE_MIN_VERSION = 0x102;

        BluetoothSdpReceiver() {
            L.d(TAG, "Registering receiver for sdp");

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothDevice.ACTION_SDP_RECORD);
            mContext.registerReceiver(this, intentFilter);
        }

        void unregisterReceivers() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_SDP_RECORD.equals(intent.getAction())) {
                L.d(TAG, "get SDP record: %s", intent.getExtras());

                Parcelable parcelable = intent.getParcelableExtra(BluetoothDevice.EXTRA_SDP_RECORD);
                if (!(parcelable instanceof SdpMasRecord)) {
                    L.d(TAG, "not SdpMasRecord: %s", parcelable);
                    return;
                }

                SdpMasRecord masRecord = (SdpMasRecord) parcelable;
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                onSdpRecord(device, supportsReply(masRecord));
            } else {
                L.w(TAG, "Ignoring unknown broadcast %s", intent.getAction());
            }
        }

        private boolean isOn(int input, int position) {
            return ((input >> position) & 1) == 1;
        }

        private boolean supportsReply(@NonNull SdpMasRecord masRecord) {
            final int version = masRecord.getProfileVersion();
            final int features = masRecord.getSupportedFeatures();
            // We only consider the device as supporting the reply feature if the version
            // is 1.02 at minimum and the feature flag is turned on.
            return version >= REPLY_FEATURE_MIN_VERSION
                    && isOn(features, REPLY_FEATURE_FLAG_POSITION);
        }
    }
}
