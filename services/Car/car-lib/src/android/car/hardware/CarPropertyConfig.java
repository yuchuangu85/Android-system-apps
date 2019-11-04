/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.hardware;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.VehicleAreaType;
import android.car.VehicleAreaType.VehicleAreaTypeValue;
import android.car.VehiclePropertyType;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents general information about car property such as data type and min/max ranges for car
 * areas (if applicable). This class supposed to be immutable, parcelable and could be passed over.
 *
 * <p>Use {@link CarPropertyConfig#newBuilder} to create an instance of this class.
 *
 * @param <T> refer to Parcel#writeValue(Object) to get a list of all supported types. The class
 * should be visible to framework as default class loader is being used here.
 *
 */
public final class CarPropertyConfig<T> implements Parcelable {
    private final int mAccess;
    private final int mAreaType;
    private final int mChangeMode;
    private final ArrayList<Integer> mConfigArray;
    private final String mConfigString;
    private final float mMaxSampleRate;
    private final float mMinSampleRate;
    private final int mPropertyId;
    private final SparseArray<AreaConfig<T>> mSupportedAreas;
    private final Class<T> mType;

    private CarPropertyConfig(int access, int areaType, int changeMode,
            ArrayList<Integer> configArray, String configString,
            float maxSampleRate, float minSampleRate, int propertyId,
            SparseArray<AreaConfig<T>> supportedAreas, Class<T> type) {
        mAccess = access;
        mAreaType = areaType;
        mChangeMode = changeMode;
        mConfigArray = configArray;
        mConfigString = configString;
        mMaxSampleRate = maxSampleRate;
        mMinSampleRate = minSampleRate;
        mPropertyId = propertyId;
        mSupportedAreas = supportedAreas;
        mType = type;
    }

    /** @hide */
    @IntDef(prefix = {"VEHICLE_PROPERTY_ACCESS"}, value = {
        VEHICLE_PROPERTY_ACCESS_NONE,
        VEHICLE_PROPERTY_ACCESS_READ,
        VEHICLE_PROPERTY_ACCESS_WRITE,
        VEHICLE_PROPERTY_ACCESS_READ_WRITE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehiclePropertyAccessType {}

    /** Property Access Unknown */
    public static final int VEHICLE_PROPERTY_ACCESS_NONE = 0;
    /** The property is readable */
    public static final int VEHICLE_PROPERTY_ACCESS_READ = 1;
    /** The property is writable */
    public static final int VEHICLE_PROPERTY_ACCESS_WRITE = 2;
    /** The property is readable and writable */
    public static final int VEHICLE_PROPERTY_ACCESS_READ_WRITE = 3;

    /** @hide */
    @IntDef(prefix = {"VEHICLE_PROPERTY_CHANGE_MODE"}, value = {
        VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
        VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
        VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VehiclePropertyChangeModeType {}

    /** Properties of this type must never be changed. */
    public static final int VEHICLE_PROPERTY_CHANGE_MODE_STATIC = 0;
    /** Properties of this type must report when there is a change. */
    public static final int VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE = 1;
    /** Properties of this type change continuously. */
    public static final int VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS = 2;

    /**
     * Return the access type of the car property.
     * <p>The access type could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_NONE}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}</li>
     * </ul>
     *
     * @return the access type of the car property.
     */
    public @VehiclePropertyAccessType int getAccess() {
        return mAccess;
    }

    /**
     * Return the area type of the car property.
     * <p>The area type could be one of the following:
     * <ul>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_GLOBAL}</li>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WINDOW}</li>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_SEAT}</li>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_DOOR}</li>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_MIRROR}</li>
     *   <li>{@link VehicleAreaType#VEHICLE_AREA_TYPE_WHEEL}</li>
     * </ul>
     *
     * @return the area type of the car property.
     */
    public @VehicleAreaTypeValue int getAreaType() {
        return mAreaType;
    }

    /**
     * Return the change mode of the car property.
     *
     * <p>The change mode could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC }</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}</li>
     * </ul>
     *
     * @return the change mode of properties.
     */
    public @VehiclePropertyChangeModeType int getChangeMode() {
        return mChangeMode;
    }

    /**
     *
     * @return Additional configuration parameters. For different properties, configArrays have
     * different information.
     */
    @NonNull
    public List<Integer> getConfigArray() {
        return Collections.unmodifiableList(mConfigArray);
    }

    /**
     *
     * @return Some properties may require additional information passed over this
     * string. Most properties do not need to set this.
     * @hide
     */
    public String getConfigString() {
        return mConfigString;
    }

    /**
     *
     * @return Max sample rate in Hz. Must be defined for VehiclePropertyChangeMode::CONTINUOUS
     * return 0 if change mode is not continuous.
     */
    public float getMaxSampleRate() {
        return mMaxSampleRate;
    }

    /**
     *
     * @return Min sample rate in Hz.Must be defined for VehiclePropertyChangeMode::CONTINUOUS
     * return 0 if change mode is not continuous.
     */
    public float getMinSampleRate() {
        return mMinSampleRate;
    }

    /**
     * @return Property identifier
     */
    public int getPropertyId() {
        return mPropertyId;
    }

    /**
     * @return Value type of VehicleProperty.
     * @hide
     */
    @SystemApi
    @NonNull
    public Class<T> getPropertyType() {
        return mType;
    }

    /**
     *
     * @return true if this property doesn't hold car area-specific configuration.
     */
    public boolean isGlobalProperty() {
        return mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
    }

    /**
     *
     * @return the number of areaIds for properties.
     * @hide
     */
    public int getAreaCount() {
        return mSupportedAreas.size();
    }

    /**
     *
     * @return Array of areaIds. An AreaID is a combination of one or more areas,
     * and is represented using a bitmask of Area enums. Different AreaTypes may
     * not be mixed in a single AreaID. For instance, a window area cannot be
     * combined with a seat area in an AreaID.
     * Rules for mapping a zoned property to AreaIDs:
     *  - A property must be mapped to an array of AreaIDs that are impacted when
     *    the property value changes.
     *  - Each element in the array must represent an AreaID, in which, the
     *    property value can only be changed together in all the areas within
     *    an AreaID and never independently. That is, when the property value
     *    changes in one of the areas in an AreaID in the array, then it must
     *    automatically change in all other areas in the AreaID.
     *  - The property value must be independently controllable in any two
     *    different AreaIDs in the array.
     *  - An area must only appear once in the array of AreaIDs. That is, an
     *    area must only be part of a single AreaID in the array.
     */
    @NonNull
    public int[] getAreaIds() {
        int[] areaIds = new int[mSupportedAreas.size()];
        for (int i = 0; i < areaIds.length; i++) {
            areaIds[i] = mSupportedAreas.keyAt(i);
        }
        return areaIds;
    }

    /**
     * @return  the first areaId.
     * Throws {@link IllegalStateException} if supported area count not equals to one.
     * @hide
     */
    public int getFirstAndOnlyAreaId() {
        if (mSupportedAreas.size() != 1) {
            throw new IllegalStateException("Expected one and only area in this property. Prop: 0x"
                    + Integer.toHexString(mPropertyId));
        }
        return mSupportedAreas.keyAt(0);
    }

    /**
     *
     * @param areaId
     * @return true if areaId is existing.
     * @hide
     */
    public boolean hasArea(int areaId) {
        return mSupportedAreas.indexOfKey(areaId) >= 0;
    }

    /**
     *
     * @param areaId
     * @return Min value in given areaId. Null if not have min value in given area.
     */
    @Nullable
    public T getMinValue(int areaId) {
        AreaConfig<T> area = mSupportedAreas.get(areaId);
        return area == null ? null : area.getMinValue();
    }

    /**
     *
     * @param areaId
     * @return Max value in given areaId. Null if not have max value in given area.
     */
    @Nullable
    public T getMaxValue(int areaId) {
        AreaConfig<T> area = mSupportedAreas.get(areaId);
        return area == null ? null : area.getMaxValue();
    }

    /**
     *
     * @return Min value in areaId 0. Null if not have min value.
     */
    @Nullable
    public T getMinValue() {
        AreaConfig<T> area = mSupportedAreas.get(0);
        return area == null ? null : area.getMinValue();
    }

    /**
     *
     * @return Max value in areaId 0. Null if not have max value.
     */
    @Nullable
    public T getMaxValue() {
        AreaConfig<T> area = mSupportedAreas.get(0);
        return area == null ? null : area.getMaxValue();
    }

    @Override
    public int describeContents() {
        return 0;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAccess);
        dest.writeInt(mAreaType);
        dest.writeInt(mChangeMode);
        dest.writeInt(mConfigArray.size());
        for (int i = 0; i < mConfigArray.size(); i++) {
            dest.writeInt(mConfigArray.get(i));
        }
        dest.writeString(mConfigString);
        dest.writeFloat(mMaxSampleRate);
        dest.writeFloat(mMinSampleRate);
        dest.writeInt(mPropertyId);
        dest.writeInt(mSupportedAreas.size());
        for (int i = 0; i < mSupportedAreas.size(); i++) {
            dest.writeInt(mSupportedAreas.keyAt(i));
            dest.writeParcelable(mSupportedAreas.valueAt(i), flags);
        }
        dest.writeString(mType.getName());
    }

    @SuppressWarnings("unchecked")
    private CarPropertyConfig(Parcel in) {
        mAccess = in.readInt();
        mAreaType = in.readInt();
        mChangeMode = in.readInt();
        int configArraySize = in.readInt();
        mConfigArray = new ArrayList<Integer>(configArraySize);
        for (int i = 0; i < configArraySize; i++) {
            mConfigArray.add(in.readInt());
        }
        mConfigString = in.readString();
        mMaxSampleRate = in.readFloat();
        mMinSampleRate = in.readFloat();
        mPropertyId = in.readInt();
        int areaSize = in.readInt();
        mSupportedAreas = new SparseArray<>(areaSize);
        for (int i = 0; i < areaSize; i++) {
            int areaId = in.readInt();
            AreaConfig<T> area = in.readParcelable(getClass().getClassLoader());
            mSupportedAreas.put(areaId, area);
        }
        String className = in.readString();
        try {
            mType = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
    }

    public static final Creator<CarPropertyConfig> CREATOR = new Creator<CarPropertyConfig>() {
        @Override
        public CarPropertyConfig createFromParcel(Parcel in) {
            return new CarPropertyConfig(in);
        }

        @Override
        public CarPropertyConfig[] newArray(int size) {
            return new CarPropertyConfig[size];
        }
    };

    /** @hide */
    @Override
    public String toString() {
        return "CarPropertyConfig{"
                + "mPropertyId=" + mPropertyId
                + ", mAccess=" + mAccess
                + ", mAreaType=" + mAreaType
                + ", mChangeMode=" + mChangeMode
                + ", mConfigArray=" + mConfigArray
                + ", mConfigString=" + mConfigString
                + ", mMaxSampleRate=" + mMaxSampleRate
                + ", mMinSampleRate=" + mMinSampleRate
                + ", mSupportedAreas=" + mSupportedAreas
                + ", mType=" + mType
                + '}';
    }

    /**
     * Represents min/max value of car property.
     * @param <T>
     * @hide
     */
    public static class AreaConfig<T> implements Parcelable {
        @Nullable private final T mMinValue;
        @Nullable private final T mMaxValue;

        private AreaConfig(T minValue, T maxValue) {
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        public static final Parcelable.Creator<AreaConfig<Object>> CREATOR
                = getCreator(Object.class);

        private static <E> Parcelable.Creator<AreaConfig<E>> getCreator(final Class<E> clazz) {
            return new Creator<AreaConfig<E>>() {
                @Override
                public AreaConfig<E> createFromParcel(Parcel source) {
                    return new AreaConfig<>(source);
                }

                @Override @SuppressWarnings("unchecked")
                public AreaConfig<E>[] newArray(int size) {
                    return (AreaConfig<E>[]) Array.newInstance(clazz, size);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private AreaConfig(Parcel in) {
            mMinValue = (T) in.readValue(getClass().getClassLoader());
            mMaxValue = (T) in.readValue(getClass().getClassLoader());
        }

        @Nullable public T getMinValue() { return mMinValue; }
        @Nullable public T getMaxValue() { return mMaxValue; }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeValue(mMinValue);
            dest.writeValue(mMaxValue);
        }

        @Override
        public String toString() {
            return "CarAreaConfig{" +
                    "mMinValue=" + mMinValue +
                    ", mMaxValue=" + mMaxValue +
                    '}';
        }
    }

    /**
     * Prepare an instance of CarPropertyConfig
     *
     * @return Builder<T>
     * @hide
     */
    @SystemApi
    public static <T> Builder<T> newBuilder(Class<T> type, int propertyId, int areaType,
                                            int areaCapacity) {
        return new Builder<>(areaCapacity, areaType, propertyId, type);
    }


    /**
     * Prepare an instance of CarPropertyConfig
     *
     * @return Builder<T>
     * @hide
     */
    public static <T> Builder<T> newBuilder(Class<T> type, int propertyId, int areaType) {
        return new Builder<>(0, areaType, propertyId, type);
    }


    /**
     * @param <T>
     * @hide
     * */
    @SystemApi
    public static class Builder<T> {
        private int mAccess;
        private final int mAreaType;
        private int mChangeMode;
        private final ArrayList<Integer> mConfigArray;
        private String mConfigString;
        private float mMaxSampleRate;
        private float mMinSampleRate;
        private final int mPropertyId;
        private final SparseArray<AreaConfig<T>> mSupportedAreas;
        private final Class<T> mType;

        private Builder(int areaCapacity, int areaType, int propertyId, Class<T> type) {
            mAreaType = areaType;
            mConfigArray = new ArrayList<>();
            mPropertyId = propertyId;
            if (areaCapacity != 0) {
                mSupportedAreas = new SparseArray<>(areaCapacity);
            } else {
                mSupportedAreas = new SparseArray<>();
            }
            mType = type;
        }

        /**
         * Add supported areas parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> addAreas(int[] areaIds) {
            for (int id : areaIds) {
                mSupportedAreas.put(id, null);
            }
            return this;
        }

        /**
         * Add area to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> addArea(int areaId) {
            return addAreaConfig(areaId, null, null);
        }

        /**
         * Add areaConfig to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> addAreaConfig(int areaId, T min, T max) {
            if (!isRangeAvailable(min, max)) {
                mSupportedAreas.put(areaId, null);
            } else {
                mSupportedAreas.put(areaId, new AreaConfig<>(min, max));
            }
            return this;
        }

        /**
         * Set access parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setAccess(int access) {
            mAccess = access;
            return this;
        }

        /**
         * Set changeMode parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setChangeMode(int changeMode) {
            mChangeMode = changeMode;
            return this;
        }

        /**
         * Set configArray parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setConfigArray(ArrayList<Integer> configArray) {
            mConfigArray.clear();
            mConfigArray.addAll(configArray);
            return this;
        }

        /**
         * Set configString parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setConfigString(String configString) {
            mConfigString = configString;
            return this;
        }

        /**
         * Set maxSampleRate parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setMaxSampleRate(float maxSampleRate) {
            mMaxSampleRate = maxSampleRate;
            return this;
        }

        /**
         * Set minSampleRate parameter to CarPropertyConfig
         *
         * @return Builder<T>
         */
        public Builder<T> setMinSampleRate(float minSampleRate) {
            mMinSampleRate = minSampleRate;
            return this;
        }

        public CarPropertyConfig<T> build() {
            return new CarPropertyConfig<>(mAccess, mAreaType, mChangeMode, mConfigArray,
                                           mConfigString, mMaxSampleRate, mMinSampleRate,
                                           mPropertyId, mSupportedAreas, mType);
        }

        private boolean isRangeAvailable(T min, T max) {
            if (min == null || max == null) {
                return false;
            }
            int propertyType = mPropertyId & VehiclePropertyType.MASK;
            switch (propertyType) {
                case VehiclePropertyType.INT32:
                    return (Integer) min  != 0 || (Integer) max != 0;
                case VehiclePropertyType.INT64:
                    return (Long) min != 0L || (Long) max != 0L;
                case VehiclePropertyType.FLOAT:
                    return (Float) min != 0f || (Float) max != 0f;
                default:
                    return false;
            }
        }
    }
}
