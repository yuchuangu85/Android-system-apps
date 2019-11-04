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

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.*;

/**
 * A Vehicle Map Service layer with a list of publisher IDs it is associated with.
 *
 * @hide
 */
@SystemApi
public final class VmsAssociatedLayer implements Parcelable {
    private final VmsLayer mLayer;
    private final Set<Integer> mPublisherIds;

    /**
     * Constructs a new layer offering.
     *
     * @param layer layer being offered
     * @param publisherIds IDs of publishers associated with the layer
     */
    public VmsAssociatedLayer(@NonNull VmsLayer layer, @NonNull Set<Integer> publisherIds) {
        mLayer = Preconditions.checkNotNull(layer, "layer cannot be null");
        mPublisherIds = Collections.unmodifiableSet(publisherIds);
    }

    /**
     * @return layer being offered
     */
    @NonNull
    public VmsLayer getVmsLayer() {
        return mLayer;
    }

    /**
     * @return IDs of publishers associated with the layer
     */
    @NonNull
    public Set<Integer> getPublisherIds() {
        return mPublisherIds;
    }

    @Override
    public String toString() {
        return "VmsAssociatedLayer{ VmsLayer: " + mLayer + ", Publishers: " + mPublisherIds + "}";
    }

    public static final Parcelable.Creator<VmsAssociatedLayer> CREATOR =
            new Parcelable.Creator<VmsAssociatedLayer>() {
                public VmsAssociatedLayer createFromParcel(Parcel in) {
                    return new VmsAssociatedLayer(in);
                }

                public VmsAssociatedLayer[] newArray(int size) {
                    return new VmsAssociatedLayer[size];
                }
            };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mLayer, flags);
        out.writeArray(mPublisherIds.toArray());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VmsAssociatedLayer)) {
            return false;
        }
        VmsAssociatedLayer p = (VmsAssociatedLayer) o;
        return Objects.equals(p.mLayer, mLayer) && p.mPublisherIds.equals(mPublisherIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLayer, mPublisherIds);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private VmsAssociatedLayer(Parcel in) {
        mLayer = in.readParcelable(VmsLayer.class.getClassLoader());

        Object[] objects = in.readArray(Integer.class.getClassLoader());
        Integer[] integers = Arrays.copyOf(objects, objects.length, Integer[].class);

        mPublisherIds = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(integers)));
    }
}
