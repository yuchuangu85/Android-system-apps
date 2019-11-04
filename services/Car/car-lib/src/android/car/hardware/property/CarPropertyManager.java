/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.car.hardware.property;

import static java.lang.Integer.toHexString;

import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.CarManagerBase;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Handler;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.CarRatedFloatListeners;
import com.android.car.internal.SingleMessageHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/**
 * Provides an application interface for interacting with the Vehicle specific properties.
 * For details about the individual properties, see the descriptions in
 * hardware/interfaces/automotive/vehicle/types.hal
 */
public class CarPropertyManager implements CarManagerBase {
    private static final boolean DBG = false;
    private static final String TAG = "CarPropertyManager";
    private static final int MSG_GENERIC_EVENT = 0;
    private final SingleMessageHandler<CarPropertyEvent> mHandler;
    private final ICarProperty mService;

    private CarPropertyEventListenerToService mCarPropertyEventToService;

    /** Record of locally active properties. Key is propertyId */
    private final SparseArray<CarPropertyListeners> mActivePropertyListener =
            new SparseArray<>();
    /** Record of properties' configs. Key is propertyId */
    private final SparseArray<CarPropertyConfig> mConfigMap = new SparseArray<>();

    /**
     * Application registers {@link CarPropertyEventCallback} object to receive updates and changes
     * to subscribed Vehicle specific properties.
     */
    public interface CarPropertyEventCallback {
        /**
         * Called when a property is updated
         * @param value Property that has been updated.
         */
        void onChangeEvent(CarPropertyValue value);

        /**
         * Called when an error is detected with a property
         * @param propId Property ID which is detected an error.
         * @param zone Zone which is detected an error.
         */
        void onErrorEvent(int propId, int zone);
    }

    /** Read ON_CHANGE sensors */
    public static final float SENSOR_RATE_ONCHANGE = 0f;
    /** Read sensors at the rate of  1 hertz */
    public static final float SENSOR_RATE_NORMAL = 1f;
    /** Read sensors at the rate of 5 hertz */
    public static final float SENSOR_RATE_UI = 5f;
    /** Read sensors at the rate of 10 hertz */
    public static final float SENSOR_RATE_FAST = 10f;
    /** Read sensors at the rate of 100 hertz */
    public static final float SENSOR_RATE_FASTEST = 100f;

    /**
     * Get an instance of the CarPropertyManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @param service ICarProperty instance
     * @param handler The handler to deal with CarPropertyEvent.
     * @hide
     */
    public CarPropertyManager(@NonNull ICarProperty service, @Nullable Handler handler) {
        mService = service;
        try {
            List<CarPropertyConfig> configs = mService.getPropertyList();
            for (CarPropertyConfig carPropertyConfig : configs) {
                mConfigMap.put(carPropertyConfig.getPropertyId(), carPropertyConfig);
            }
        } catch (Exception e) {
            Log.e(TAG, "getPropertyList exception ", e);
            throw new RuntimeException(e);
        }
        if (handler == null) {
            mHandler = null;
            return;
        }
        mHandler = new SingleMessageHandler<CarPropertyEvent>(handler.getLooper(),
            MSG_GENERIC_EVENT) {
            @Override
            protected void handleEvent(CarPropertyEvent event) {
                CarPropertyListeners listeners;
                synchronized (mActivePropertyListener) {
                    listeners = mActivePropertyListener.get(
                        event.getCarPropertyValue().getPropertyId());
                }
                if (listeners != null) {
                    switch (event.getEventType()) {
                        case CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE:
                            listeners.onPropertyChanged(event);
                            break;
                        case CarPropertyEvent.PROPERTY_EVENT_ERROR:
                            listeners.onErrorEvent(event);
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                }
            }
        };
    }

    /**
     * Register {@link CarPropertyEventCallback} to get property updates. Multiple listeners
     * can be registered for a single property or the same listener can be used for different
     * properties. If the same listener is registered again for the same property, it will be
     * updated to new rate.
     * <p>Rate could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_ONCHANGE}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_NORMAL}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_UI}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FAST}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FASTEST}</li>
     * </ul>
     * <p>
     * <b>Note:</b>Rate has no effect if the property has one of the following change modes:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}</li>
     * </ul>
     * See {@link CarPropertyConfig#getChangeMode()} for details.
     * If rate is higher than {@link CarPropertyConfig#getMaxSampleRate()}, it will be registered
     * with max sample rate.
     * If rate is lower than {@link CarPropertyConfig#getMinSampleRate()}, it will be registered
     * with min sample rate.
     *
     * @param callback CarPropertyEventCallback to be registered.
     * @param propertyId PropertyId to subscribe
     * @param rate how fast the property events are delivered in Hz.
     * @return true if the listener is successfully registered.
     * @throws SecurityException if missing the appropriate permission.
     */
    public boolean registerCallback(@NonNull CarPropertyEventCallback callback,
            int propertyId, @FloatRange(from = 0.0, to = 100.0) float rate) {
        synchronized (mActivePropertyListener) {
            if (mCarPropertyEventToService == null) {
                mCarPropertyEventToService = new CarPropertyEventListenerToService(this);
            }
            CarPropertyConfig config = mConfigMap.get(propertyId);
            if (config == null) {
                Log.e(TAG, "registerListener:  propId is not in config list:  " + propertyId);
                return false;
            }
            if (config.getChangeMode() == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE) {
                rate = SENSOR_RATE_ONCHANGE;
            }
            boolean needsServerUpdate = false;
            CarPropertyListeners listeners;
            listeners = mActivePropertyListener.get(propertyId);
            if (listeners == null) {
                listeners = new CarPropertyListeners(rate);
                mActivePropertyListener.put(propertyId, listeners);
                needsServerUpdate = true;
            }
            if (listeners.addAndUpdateRate(callback, rate)) {
                needsServerUpdate = true;
            }
            if (needsServerUpdate) {
                if (!registerOrUpdatePropertyListener(propertyId, rate)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean registerOrUpdatePropertyListener(int propertyId, float rate) {
        try {
            mService.registerListener(propertyId, rate, mCarPropertyEventToService);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return true;
    }

    private static class CarPropertyEventListenerToService extends ICarPropertyEventListener.Stub{
        private final WeakReference<CarPropertyManager> mMgr;

        CarPropertyEventListenerToService(CarPropertyManager mgr) {
            mMgr = new WeakReference<>(mgr);
        }

        @Override
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            CarPropertyManager manager = mMgr.get();
            if (manager != null) {
                manager.handleEvent(events);
            }
        }
    }

    private void handleEvent(List<CarPropertyEvent> events) {
        if (mHandler != null) {
            mHandler.sendEvents(events);
        }
    }

    /**
     * Stop getting property update for the given callback. If there are multiple registrations for
     * this callback, all listening will be stopped.
     * @param callback CarPropertyEventCallback to be unregistered.
     */
    public void unregisterCallback(@NonNull CarPropertyEventCallback callback) {
        synchronized (mActivePropertyListener) {
            int [] propertyIds = new int[mActivePropertyListener.size()];
            for (int i = 0; i < mActivePropertyListener.size(); i++) {
                propertyIds[i] = mActivePropertyListener.keyAt(i);
            }
            for (int prop : propertyIds) {
                doUnregisterListenerLocked(callback, prop);
            }
        }
    }

    /**
     * Stop getting property update for the given callback and property. If the same callback is
     * used for other properties, those subscriptions will not be affected.
     *
     * @param callback CarPropertyEventCallback to be unregistered.
     * @param propertyId PropertyId to be unregistered.
     */
    public void unregisterCallback(@NonNull CarPropertyEventCallback callback, int propertyId) {
        synchronized (mActivePropertyListener) {
            doUnregisterListenerLocked(callback, propertyId);
        }
    }

    private void doUnregisterListenerLocked(CarPropertyEventCallback listener, int propertyId) {
        CarPropertyListeners listeners = mActivePropertyListener.get(propertyId);
        if (listeners != null) {
            boolean needsServerUpdate = false;
            if (listeners.contains(listener)) {
                needsServerUpdate = listeners.remove(listener);
            }
            if (listeners.isEmpty()) {
                try {
                    mService.unregisterListener(propertyId, mCarPropertyEventToService);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
                mActivePropertyListener.remove(propertyId);
            } else if (needsServerUpdate) {
                registerOrUpdatePropertyListener(propertyId, listeners.getRate());
            }
        }
    }

    /**
     * @return List of properties implemented by this car that the application may access.
     */
    @NonNull
    public List<CarPropertyConfig> getPropertyList() {
        List<CarPropertyConfig> configs = new ArrayList<>(mConfigMap.size());
        for (int i = 0; i < mConfigMap.size(); i++) {
            configs.add(mConfigMap.valueAt(i));
        }
        return configs;
    }

    /**
     * @param propertyIds property ID list
     * @return List of properties implemented by this car in given property ID list that application
     *          may access.
     */
    @NonNull
    public List<CarPropertyConfig> getPropertyList(@NonNull ArraySet<Integer> propertyIds) {
        List<CarPropertyConfig> configs = new ArrayList<>();
        for (int propId : propertyIds) {
            CarPropertyConfig config = mConfigMap.get(propId);
            if (config != null) {
                configs.add(config);
            }
        }
        return configs;
    }

    /**
     * Return read permission string for given property ID.
     *
     * @param propId Property ID to query
     * @return String Permission needed to read this property.  NULL if propId not available.
     * @hide
     */
    @Nullable
    public String getReadPermission(int propId) {
        if (DBG) {
            Log.d(TAG, "getReadPermission, propId: 0x" + toHexString(propId));
        }
        try {
            return mService.getReadPermission(propId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Return write permission string for given property ID.
     *
     * @param propId Property ID to query
     * @return String Permission needed to write this property.  NULL if propId not available.
     * @hide
     */
    @Nullable
    public String getWritePermission(int propId) {
        if (DBG) {
            Log.d(TAG, "getWritePermission, propId: 0x" + toHexString(propId));
        }
        try {
            return mService.getWritePermission(propId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }


    /**
     * Check whether a given property is available or disabled based on the car's current state.
     * @param propId Property Id
     * @param area AreaId of property
     * @return true if STATUS_AVAILABLE, false otherwise (eg STATUS_UNAVAILABLE)
     */
    public boolean isPropertyAvailable(int propId, int area) {
        try {
            CarPropertyValue propValue = mService.getProperty(propId, area);
            return (propValue != null)
                    && (propValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns value of a bool property
     *
     * @param prop Property ID to get
     * @param area Area of the property to get
     * @return value of a bool property.
     */
    public boolean getBooleanProperty(int prop, int area) {
        CarPropertyValue<Boolean> carProp = getProperty(Boolean.class, prop, area);
        return carProp != null ? carProp.getValue() : false;
    }

    /**
     * Returns value of a float property
     *
     * @param prop Property ID to get
     * @param area Area of the property to get
     */
    public float getFloatProperty(int prop, int area) {
        CarPropertyValue<Float> carProp = getProperty(Float.class, prop, area);
        return carProp != null ? carProp.getValue() : 0f;
    }

    /**
     * Returns value of a integer property
     *
     * @param prop Property ID to get
     * @param area Zone of the property to get
     */
    public int getIntProperty(int prop, int area) {
        CarPropertyValue<Integer> carProp = getProperty(Integer.class, prop, area);
        return carProp != null ? carProp.getValue() : 0;
    }

    /**
     * Returns value of a integer array property
     *
     * @param prop Property ID to get
     * @param area Zone of the property to get
     */
    @NonNull
    public int[] getIntArrayProperty(int prop, int area) {
        CarPropertyValue<Integer[]> carProp = getProperty(Integer[].class, prop, area);
        return carProp != null ? toIntArray(carProp.getValue()) : new int[0];
    }

    private static int[] toIntArray(Integer[] input) {
        int len = input.length;
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) {
            arr[i] = input[i];
        }
        return arr;
    }

    /**
     * Return CarPropertyValue
     *
     * @param clazz The class object for the CarPropertyValue
     * @param propId Property ID to get
     * @param areaId Zone of the property to get
     * @throws IllegalArgumentException if there is invalid property type.
     * @return CarPropertyValue. Null if property's id is invalid.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <E> CarPropertyValue<E> getProperty(@NonNull Class<E> clazz, int propId, int areaId) {
        if (DBG) {
            Log.d(TAG, "getProperty, propId: 0x" + toHexString(propId)
                    + ", areaId: 0x" + toHexString(areaId) + ", class: " + clazz);
        }
        try {
            CarPropertyValue<E> propVal = mService.getProperty(propId, areaId);
            if (propVal != null && propVal.getValue() != null) {
                Class<?> actualClass = propVal.getValue().getClass();
                if (actualClass != clazz) {
                    throw new IllegalArgumentException("Invalid property type. " + "Expected: "
                            + clazz + ", but was: " + actualClass);
                }
            }
            return propVal;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Query CarPropertyValue with property id and areaId.
     * @param propId Property Id
     * @param areaId areaId
     * @param <E>
     * @return CarPropertyValue. Null if property's id is invalid.
     */
    @Nullable
    public <E> CarPropertyValue<E> getProperty(int propId, int areaId) {
        try {
            CarPropertyValue<E> propVal = mService.getProperty(propId, areaId);
            return propVal;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set value of car property by areaId.
     * @param clazz The class object for the CarPropertyValue
     * @param propId Property ID
     * @param areaId areaId
     * @param val Value of CarPropertyValue
     * @param <E> data type of the given property, for example property that was
     * defined as {@code VEHICLE_VALUE_TYPE_INT32} in vehicle HAL could be accessed using
     * {@code Integer.class}
     */
    public <E> void setProperty(@NonNull Class<E> clazz, int propId, int areaId, @NonNull E val) {
        if (DBG) {
            Log.d(TAG, "setProperty, propId: 0x" + toHexString(propId)
                    + ", areaId: 0x" + toHexString(areaId) + ", class: " + clazz + ", val: " + val);
        }
        try {
            mService.setProperty(new CarPropertyValue<>(propId, areaId, val));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Modifies a property.  If the property modification doesn't occur, an error event shall be
     * generated and propagated back to the application.
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification.
     * @param val Value to set
     */
    public void setBooleanProperty(int prop, int areaId, boolean val) {
        setProperty(Boolean.class, prop, areaId, val);
    }

    /**
     * Set float value of property
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification
     * @param val Value to set
     */
    public void setFloatProperty(int prop, int areaId, float val) {
        setProperty(Float.class, prop, areaId, val);
    }

    /**
     * Set int value of property
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification
     * @param val Value to set
     */
    public void setIntProperty(int prop, int areaId, int val) {
        setProperty(Integer.class, prop, areaId, val);
    }


    private class CarPropertyListeners extends CarRatedFloatListeners<CarPropertyEventCallback> {
        CarPropertyListeners(float rate) {
            super(rate);
        }
        void onPropertyChanged(final CarPropertyEvent event) {
            // throw away old sensor data as oneway binder call can change order.
            long updateTime = event.getCarPropertyValue().getTimestamp();
            if (updateTime < mLastUpdateTime) {
                Log.w(TAG, "dropping old property data");
                return;
            }
            mLastUpdateTime = updateTime;
            List<CarPropertyEventCallback> listeners;
            synchronized (mActivePropertyListener) {
                listeners = new ArrayList<>(getListeners());
            }
            listeners.forEach(new Consumer<CarPropertyEventCallback>() {
                @Override
                public void accept(CarPropertyEventCallback listener) {
                    if (needUpdate(listener, updateTime)) {
                        listener.onChangeEvent(event.getCarPropertyValue());
                    }
                }
            });
        }

        void onErrorEvent(final CarPropertyEvent event) {
            List<CarPropertyEventCallback> listeners;
            CarPropertyValue value = event.getCarPropertyValue();
            synchronized (mActivePropertyListener) {
                listeners = new ArrayList<>(getListeners());
            }
            listeners.forEach(new Consumer<CarPropertyEventCallback>() {
                @Override
                public void accept(CarPropertyEventCallback listener) {
                    if (DBG) {
                        Log.d(TAG, new StringBuilder().append("onErrorEvent for ")
                                        .append("property: ").append(value.getPropertyId())
                                        .append(" areaId: ").append(value.getAreaId())
                                        .toString());
                    }
                    listener.onErrorEvent(value.getPropertyId(), value.getAreaId());
                }
            });
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (mActivePropertyListener) {
            mActivePropertyListener.clear();
            mCarPropertyEventToService = null;
        }
    }
}
