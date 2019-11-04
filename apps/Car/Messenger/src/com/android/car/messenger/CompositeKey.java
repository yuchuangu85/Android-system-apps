package com.android.car.messenger;

import java.util.Map;
import java.util.Objects;

/**
 * A composite key used for {@link Map} lookups, using two strings for
 * checking equality and hashing.
 */
public abstract class CompositeKey {
    private final String mDeviceAddress;
    private final String mSubKey;

    protected CompositeKey(String deviceAddress, String subKey) {
        mDeviceAddress = deviceAddress;
        mSubKey = subKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CompositeKey)) {
            return false;
        }

        CompositeKey that = (CompositeKey) o;
        return Objects.equals(mDeviceAddress, that.mDeviceAddress)
                && Objects.equals(mSubKey, that.mSubKey);
    }

    /**
     * Returns true if the device address of this composite key equals {@code deviceAddress}.
     *
     * @param deviceAddress the device address which is compared to this key's device address
     * @return true if the device addresses match
     */
    public boolean matches(String deviceAddress) {
        return mDeviceAddress.equals(deviceAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDeviceAddress, mSubKey);
    }

    @Override
    public String toString() {
        return String.format("%s, deviceAddress: %s, subKey: %s",
                getClass().getSimpleName(), mDeviceAddress, mSubKey);
    }

    /** Returns this composite key's device address. */
    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    /** Returns this composite key's sub key. */
    public String getSubKey() {
        return mSubKey;
    }
}
