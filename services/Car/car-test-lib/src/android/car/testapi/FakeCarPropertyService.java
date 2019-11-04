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

package android.car.testapi;

import static android.car.hardware.property.CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE;

import static java.lang.Integer.toHexString;

import android.annotation.Nullable;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.os.RemoteException;

import com.android.car.internal.PropertyPermissionMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This is fake implementation of the service which is used in
 * {@link android.car.hardware.property.CarPropertyManager}.
 *
 * @hide
 */
class FakeCarPropertyService extends ICarProperty.Stub implements CarPropertyController {
    private final Map<Integer, CarPropertyConfig> mConfigs = new HashMap<>();
    private final Map<PropKey, CarPropertyValue> mValues = new HashMap<>();

    private final PropertyPermissionMapping mPermissions = new PropertyPermissionMapping();

    // Contains a list of values that were set from the manager.
    private final ArrayList<CarPropertyValue<?>> mValuesSet = new ArrayList<>();

    // Mapping between propertyId and a set of listeners.
    private final Map<Integer, Set<ListenerInfo>> mListeners = new HashMap<>();

    @Override
    public void registerListener(int propId, float rate, ICarPropertyEventListener listener)
            throws RemoteException {
        Set<ListenerInfo> propListeners = mListeners.get(propId);
        if (propListeners == null) {
            propListeners = new HashSet<>();
            mListeners.put(propId, propListeners);
        }

        propListeners.add(new ListenerInfo(listener));
    }

    @Override
    public void unregisterListener(int propId, ICarPropertyEventListener listener)
            throws RemoteException {
        Set<ListenerInfo> propListeners = mListeners.get(propId);
        if (propListeners != null && propListeners.remove(new ListenerInfo(listener))) {
            if (propListeners.isEmpty()) {
                mListeners.remove(propId);
            }
        }
    }

    @Override
    public List<CarPropertyConfig> getPropertyList() throws RemoteException {
        return new ArrayList<>(mConfigs.values());
    }

    @Override
    public CarPropertyValue getProperty(int prop, int zone) throws RemoteException {
        return mValues.get(PropKey.of(prop, zone));
    }

    @Override
    public void setProperty(CarPropertyValue prop) throws RemoteException {
        mValues.put(PropKey.of(prop), prop);
        mValuesSet.add(prop);
        sendEvent(prop);
    }

    @Override
    public String getReadPermission(int propId) throws RemoteException {
        return mConfigs.containsKey(propId) ? mPermissions.getReadPermission(propId) : null;
    }

    @Override
    public String getWritePermission(int propId) throws RemoteException {
        return mConfigs.containsKey(propId) ? mPermissions.getWritePermission(propId) : null;
    }

    @Override
    public CarPropertyController addProperty(Integer propId, Object value) {
        int areaType = getVehicleAreaType(propId);
        Class<?> type = getPropertyType(propId);
        CarPropertyConfig.Builder<?> builder = CarPropertyConfig
                .newBuilder(type, propId, areaType);
        mConfigs.put(propId, builder.build());
        if (value != null) {
            updateValues(false, new CarPropertyValue<>(propId, 0, value));
        }

        return this;
    }

    @Override
    public CarPropertyController addProperty(CarPropertyConfig<?> config,
            @Nullable CarPropertyValue<?> value) {
        mConfigs.put(config.getPropertyId(), config);
        if (value != null) {
            updateValues(false, value);
        }
        return this;
    }

    @Override
    public void updateValues(boolean triggerListeners, CarPropertyValue<?>... propValues) {
        for (CarPropertyValue v : propValues) {
            mValues.put(PropKey.of(v), v);
            if (triggerListeners) {
                sendEvent(v);
            }
        }
    }

    private void sendEvent(CarPropertyValue v) {
        Set<ListenerInfo> listeners = mListeners.get(v.getPropertyId());
        if (listeners != null) {
            for (ListenerInfo listenerInfo : listeners) {
                List<CarPropertyEvent> events = new ArrayList<>();
                events.add(new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, v));
                try {
                    listenerInfo.mListener.onEvent(events);
                } catch (RemoteException e) {
                    // This is impossible as the code runs within the same process in test.
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public List<CarPropertyValue<?>> getSetValues() {
        // Explicitly return the instance of this object rather than copying it such that test code
        // will have a chance to clear this list if needed.
        return mValuesSet;
    }

    /** Consists of property id and area */
    private static class PropKey {
        final int mPropId;
        final int mAreaId;

        private PropKey(int propId, int areaId) {
            this.mPropId = propId;
            this.mAreaId = areaId;
        }

        static PropKey of(int propId, int areaId) {
            return new PropKey(propId, areaId);
        }

        static PropKey of(CarPropertyValue carPropertyValue) {
            return of(carPropertyValue.getPropertyId(), carPropertyValue.getAreaId());
        }

        @Override

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PropKey)) {
                return false;
            }
            PropKey propKey = (PropKey) o;
            return mPropId == propKey.mPropId && mAreaId == propKey.mAreaId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPropId, mAreaId);
        }
    }

    private static class ListenerInfo {
        private final ICarPropertyEventListener mListener;

        ListenerInfo(ICarPropertyEventListener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ListenerInfo)) {
                return false;
            }
            ListenerInfo that = (ListenerInfo) o;
            return Objects.equals(mListener, that.mListener);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mListener);
        }
    }

    private static Class<?> getPropertyType(int propId) {
        int type = propId & VehiclePropertyType.MASK;
        switch (type) {
            case VehiclePropertyType.BOOLEAN:
                return Boolean.class;
            case VehiclePropertyType.FLOAT:
                return Float.class;
            case VehiclePropertyType.INT32:
                return Integer.class;
            case VehiclePropertyType.INT64:
                return Long.class;
            case VehiclePropertyType.FLOAT_VEC:
                return Float[].class;
            case VehiclePropertyType.INT32_VEC:
                return Integer[].class;
            case VehiclePropertyType.INT64_VEC:
                return Long[].class;
            case VehiclePropertyType.STRING:
                return String.class;
            case VehiclePropertyType.BYTES:
                return byte[].class;
            case VehiclePropertyType.MIXED:
                return Object.class;
            default:
                throw new IllegalArgumentException("Unexpected type: " + toHexString(type));
        }
    }

    private static int getVehicleAreaType(int propId) {
        int halArea = propId & VehicleArea.MASK;
        switch (halArea) {
            case VehicleArea.GLOBAL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
            case VehicleArea.SEAT:
                return VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
            case VehicleArea.DOOR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_DOOR;
            case VehicleArea.WINDOW:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW;
            case VehicleArea.MIRROR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR;
            case VehicleArea.WHEEL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL;
            default:
                throw new RuntimeException("Unsupported area type " + halArea);
        }
    }

    /** Copy from VHAL generated file VehicleArea.java */
    private static final class VehicleArea {
        static final int GLOBAL = 16777216 /* 0x01000000 */;
        static final int WINDOW = 50331648 /* 0x03000000 */;
        static final int MIRROR = 67108864 /* 0x04000000 */;
        static final int SEAT = 83886080 /* 0x05000000 */;
        static final int DOOR = 100663296 /* 0x06000000 */;
        static final int WHEEL = 117440512 /* 0x07000000 */;
        static final int MASK = 251658240 /* 0x0f000000 */;
    }
}
