/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.vms;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * A Vehicle Map Service layer, which can be offered or subscribed to by clients.
 *
 * The full layer definition is used when routing packets, with each layer having the following
 * properties:
 *
 * <ul>
 * <li>Type: Type of data being published.</li>
 * <li>Subtype: Type of packet being published.</li>
 * <li>Version: Major version of the packet format. Different versions are not guaranteed to be
 * compatible.</li>
 * </ul>
 *
 * See the Vehicle Maps Service partner documentation for the set of valid types and subtypes.
 *
 * @hide
 */
@SystemApi
public final class VmsLayer implements Parcelable {
    private int mType;
    private int mSubtype;
    private int mVersion;

    /**
     * Constructs a new layer definition.
     *
     * @param type    type of data published on the layer
     * @param subtype type of packet published on the layer
     * @param version major version of layer packet format
     */
    public VmsLayer(int type, int subtype, int version) {
        mType = type;
        mSubtype = subtype;
        mVersion = version;
    }

    /**
     * @return type of data published on the layer
     */
    public int getType() {
        return mType;
    }

    /**
     * @return type of packet published on the layer
     */
    public int getSubtype() {
        return mSubtype;
    }

    /**
     * @return major version of layer packet format
     */
    public int getVersion() {
        return mVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VmsLayer)) {
            return false;
        }
        VmsLayer p = (VmsLayer) o;
        return Objects.equals(p.mType, mType)
                && Objects.equals(p.mSubtype, mSubtype)
                && Objects.equals(p.mVersion, mVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mSubtype, mVersion);
    }

    @Override
    public String toString() {
        return "VmsLayer{ Type: " + mType + ", Sub type: " + mSubtype + ", Version: " + mVersion
                + "}";
    }

    public static final Parcelable.Creator<VmsLayer> CREATOR = new
            Parcelable.Creator<VmsLayer>() {
                public VmsLayer createFromParcel(Parcel in) {
                    return new VmsLayer(in);
                }

                public VmsLayer[] newArray(int size) {
                    return new VmsLayer[size];
                }
            };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mType);
        out.writeInt(mSubtype);
        out.writeInt(mVersion);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private VmsLayer(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        mType = in.readInt();
        mSubtype = in.readInt();
        mVersion = in.readInt();
    }
}