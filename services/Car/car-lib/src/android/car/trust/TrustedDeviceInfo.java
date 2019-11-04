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

package android.car.trust;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;


/**
 * Contains basic info of a trusted device.
 *
 * @hide
 */
@SystemApi
public final class TrustedDeviceInfo implements Parcelable {

    // TODO(b/124052887)
    public static final String DEFAULT_NAME = "Default";
    private static final String DEVICE_INFO_DELIMITER = ",";
    private final long mHandle;
    private final String mAddress;
    private final String mName;

    public TrustedDeviceInfo(long handle, @NonNull String address, @NonNull String name) {
        mHandle = handle;
        mAddress = address;
        mName = name;
    }

    /**
     * Returns the handle of current device
     *
     * @return handle which is unique for every device
     */
    public long getHandle() {
        return mHandle;
    }

    /**
     * Get local device name of current device
     *
     * @return local device name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Get MAC address of current device
     *
     * @return MAC address
     */
    @NonNull
    public String getAddress() {
        return mAddress;
    }

    public TrustedDeviceInfo(Parcel in) {
        mHandle = in.readLong();
        mName = in.readString();
        mAddress = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mHandle);
        dest.writeString(mName);
        dest.writeString(mAddress);
    }


    @Override
    public String toString() {
        return String.format("TrustedDevice{ handle=%d. address=%s, name=%s }", mHandle, mAddress,
                mName);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TrustedDeviceInfo)) {
            return false;
        }
        // If the handles of two devices are the same, then they will be considered as equals.
        TrustedDeviceInfo secondDevice = (TrustedDeviceInfo) obj;
        return mHandle == secondDevice.getHandle();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mHandle);
    }

    /**
     * Serialize the trusted device info into string
     *
     * @return string contains current trusted device information with certain format
     */
    public String serialize() {
        return String.join(DEVICE_INFO_DELIMITER, String.valueOf(mHandle), mAddress, mName);
    }

    /**
     * Deserialize the string to trusted device info
     *
     * @param deviceInfo string which contains trusted device info, should be originally generated
     *                   by serialize method
     * @return TrustedDeviceInfo object constructed from the trusted device info in the string
     */
    public static TrustedDeviceInfo deserialize(String deviceInfo) {
        String[] res = deviceInfo.split(DEVICE_INFO_DELIMITER);
        return new TrustedDeviceInfo(Long.valueOf(res[0]), res[1], res[2]);
    }

    public static final Creator CREATOR = new Creator() {
        public TrustedDeviceInfo createFromParcel(Parcel in) {
            return new TrustedDeviceInfo(in);
        }

        public TrustedDeviceInfo[] newArray(int size) {
            return new TrustedDeviceInfo[size];
        }
    };
}
