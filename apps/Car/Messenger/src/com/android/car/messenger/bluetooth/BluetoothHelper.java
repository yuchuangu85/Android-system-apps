package com.android.car.messenger.bluetooth;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * Provides helper methods for performing bluetooth actions.
 */
public class BluetoothHelper {

    /**
     * Returns a (potentially empty) immutable set of bonded (paired) devices.
     */
    public static Set<BluetoothDevice> getPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter != null) {
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices != null) {
                return devices;
            }
        }

        return Collections.emptySet();
    }

    /**
     * Helper method to send an SMS message through bluetooth.
     *
     * @param client the MAP Client used to send the message
     * @param deviceAddress the device used to send the SMS
     * @param contacts contacts to send the message to
     * @param message message to send
     * @param sentIntent callback issued once the message was sent
     * @param deliveredIntent callback issued once the message was delivered
     * @return true if the message was enqueued, false on error
     * @throws IllegalArgumentException if deviceAddress is invalid
     */
    public static boolean sendMessage(@NonNull BluetoothMapClient client,
            String deviceAddress,
            Uri[] contacts,
            String message,
            @Nullable PendingIntent sentIntent,
            @Nullable PendingIntent deliveredIntent)
            throws IllegalArgumentException {

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        }
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);

        return client.sendMessage(device, contacts, message, sentIntent, deliveredIntent);
    }
}
