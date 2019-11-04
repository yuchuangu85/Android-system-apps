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
package android.car.cluster;

import android.annotation.Nullable;
import android.app.Application;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarNotConnectedException;
import android.car.VehicleAreaType;
import android.car.cluster.sensors.Sensor;
import android.car.cluster.sensors.Sensors;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.core.util.Preconditions;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AndroidViewModel} for cluster information.
 */
public class ClusterViewModel extends AndroidViewModel {
    private static final String TAG = "Cluster.ViewModel";

    private static final int PROPERTIES_REFRESH_RATE_UI = 5;

    private float mSpeedFactor;
    private float mDistanceFactor;

    public enum NavigationActivityState {
        /** No activity has been selected to be displayed on the navigation fragment yet */
        NOT_SELECTED,
        /** An activity has been selected, but it is not yet visible to the user */
        LOADING,
        /** Navigation activity is visible to the user */
        VISIBLE,
    }

    private ComponentName mFreeNavigationActivity;
    private ComponentName mCurrentNavigationActivity;
    private final MutableLiveData<NavigationActivityState> mNavigationActivityStateLiveData =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> mNavigationFocus = new MutableLiveData<>(false);
    private Car mCar;
    private CarAppFocusManager mCarAppFocusManager;
    private CarPropertyManager mCarPropertyManager;
    private Map<Sensor<?>, MutableLiveData<?>> mSensorLiveDatas = new HashMap<>();

    private ServiceConnection mCarServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                Log.i(TAG, "onServiceConnected, name: " + name + ", service: " + service);

                registerAppFocusListener();
                registerCarPropertiesListener();
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "onServiceConnected: error obtaining manager", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected, name: " + name);
            mCarAppFocusManager = null;
            mCarPropertyManager = null;
        }
    };

    private void registerAppFocusListener() throws CarNotConnectedException {
        mCarAppFocusManager = (CarAppFocusManager) mCar.getCarManager(
                Car.APP_FOCUS_SERVICE);
        if (mCarAppFocusManager != null) {
            mCarAppFocusManager.addFocusListener(
                    (appType, active) -> setNavigationFocus(active),
                    CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        } else {
            Log.e(TAG, "onServiceConnected: unable to obtain CarAppFocusManager");
        }
    }

    private void registerCarPropertiesListener() throws CarNotConnectedException {
        Sensors sensors = Sensors.getInstance();
        mCarPropertyManager = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        for (Integer propertyId : sensors.getPropertyIds()) {
            try {
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                        propertyId, PROPERTIES_REFRESH_RATE_UI);
            } catch (SecurityException ex) {
                Log.e(TAG, "onServiceConnected: Unable to listen to car property: " + propertyId
                        + " sensors: " + sensors.getSensorsForPropertyId(propertyId), ex);
            }
        }
    }

    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG,
                                "CarProperty change: property " + value.getPropertyId() + ", area"
                                        + value.getAreaId() + ", value: " + value.getValue());
                    }
                    for (Sensor<?> sensorId : Sensors.getInstance()
                            .getSensorsForPropertyId(value.getPropertyId())) {
                        if (sensorId.mAreaId == Sensors.GLOBAL_AREA_ID
                                || (sensorId.mAreaId & value.getAreaId()) != 0) {
                            setSensorValue(sensorId, value);
                        }
                    }
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                    for (Sensor<?> sensorId : Sensors.getInstance().getSensorsForPropertyId(
                            propId)) {
                        if (sensorId.mAreaId == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                                || (sensorId.mAreaId & zone) != 0) {
                            setSensorValue(sensorId, null);
                        }
                    }
                }

                private <T> void setSensorValue(Sensor<T> id, CarPropertyValue<?> value) {
                    T newValue = value != null ? id.mAdapter.apply(value) : null;
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Sensor " + id.mName + " = " + newValue);
                    }
                    getSensorMutableLiveData(id).setValue(newValue);
                }
            };

    /**
     * New {@link ClusterViewModel} instance
     */
    public ClusterViewModel(@NonNull Application application) {
        super(application);
        mCar = Car.createCar(application, mCarServiceConnection);
        mCar.connect();

        TypedValue tv = new TypedValue();
        getApplication().getResources().getValue(R.dimen.speed_factor, tv, true);
        mSpeedFactor = tv.getFloat();

        getApplication().getResources().getValue(R.dimen.distance_factor, tv, true);
        mDistanceFactor = tv.getFloat();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mCar.disconnect();
        mCar = null;
        mCarAppFocusManager = null;
        mCarPropertyManager = null;
    }

    /**
     * Returns a {@link LiveData} providing the current state of the activity displayed on the
     * navigation fragment.
     */
    public LiveData<NavigationActivityState> getNavigationActivityState() {
        return mNavigationActivityStateLiveData;
    }

    /**
     * Returns a {@link LiveData} indicating whether navigation focus is currently being granted
     * or not. This indicates whether a navigation application is currently providing driving
     * directions.
     */
    public LiveData<Boolean> getNavigationFocus() {
        return mNavigationFocus;
    }

    /**
     * Returns a {@link LiveData} that tracks the value of a given car sensor. Each sensor has its
     * own data type. The list of all supported sensors can be found at {@link Sensors}
     *
     * @param sensor sensor to observe
     * @param <T>    data type of such sensor
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T> LiveData<T> getSensor(@NonNull Sensor<T> sensor) {
        return getSensorMutableLiveData(Preconditions.checkNotNull(sensor));
    }

    /**
     * Returns the current value of the sensor, directly from the VHAL.
     *
     * @param sensor sensor to read
     * @param <V>    VHAL data type
     * @param <T>    data type of such sensor
     */
    @Nullable
    public <T> T getSensorValue(@NonNull Sensor<T> sensor) {
        try {
            CarPropertyValue<?> value = mCarPropertyManager
                    .getProperty(sensor.mPropertyId, sensor.mAreaId);
            return sensor.mAdapter.apply(value);
        } catch (CarNotConnectedException ex) {
            Log.e(TAG, "We got disconnected from Car Service", ex);
            return null;
        }
    }

    /**
     * Returns a {@link LiveData} that tracks the fuel level in a range from 0 to 100.
     */
    public LiveData<Integer> getFuelLevel() {
        return Transformations.map(getSensor(Sensors.SENSOR_FUEL), (fuelValue) -> {
            Float fuelCapacityValue = getSensorValue(Sensors.SENSOR_FUEL_CAPACITY);
            if (fuelValue == null || fuelCapacityValue == null || fuelCapacityValue == 0) {
                return null;
            }
            if (fuelValue < 0.0f) {
                return 0;
            }
            if (fuelValue > fuelCapacityValue) {
                return 100;
            }
            return Math.round(fuelValue / fuelCapacityValue * 100f);
        });
    }

    /**
     * Returns a {@link LiveData} that tracks the RPM x 1000
     */
    public LiveData<String> getRPM() {
        return Transformations.map(getSensor(Sensors.SENSOR_RPM), (rpmValue) -> {
            return new DecimalFormat("#0.0").format(rpmValue / 1000f);
        });
    }

    /**
     * Returns a {@link LiveData} that tracks the speed in either mi/h or km/h depending on locale.
     */
    public LiveData<Integer> getSpeed() {
        return Transformations.map(getSensor(Sensors.SENSOR_SPEED), (speedValue) -> {
            return Math.round(speedValue * mSpeedFactor);
        });
    }

    /**
     * Returns a {@link LiveData} that tracks the range the vehicle has until it runs out of gas.
     */
    public LiveData<Integer> getRange() {
        return Transformations.map(getSensor(Sensors.SENSOR_FUEL_RANGE), (rangeValue) -> {
            return Math.round(rangeValue / mDistanceFactor);
        });
    }

    /**
     * Sets the activity selected to be displayed on the cluster when no driving directions are
     * being provided.
     */
    public void setFreeNavigationActivity(ComponentName activity) {
        if (!Objects.equals(activity, mFreeNavigationActivity)) {
            mFreeNavigationActivity = activity;
            updateNavigationActivityLiveData();
        }
    }

    /**
     * Sets the activity currently being displayed on the cluster.
     */
    public void setCurrentNavigationActivity(ComponentName activity) {
        if (!Objects.equals(activity, mCurrentNavigationActivity)) {
            mCurrentNavigationActivity = activity;
            updateNavigationActivityLiveData();
        }
    }

    /**
     * Sets whether navigation focus is currently being granted or not.
     */
    public void setNavigationFocus(boolean navigationFocus) {
        if (mNavigationFocus.getValue() == null || mNavigationFocus.getValue() != navigationFocus) {
            mNavigationFocus.setValue(navigationFocus);
            updateNavigationActivityLiveData();
        }
    }

    private void updateNavigationActivityLiveData() {
        NavigationActivityState newState = calculateNavigationActivityState();
        if (newState != mNavigationActivityStateLiveData.getValue()) {
            mNavigationActivityStateLiveData.setValue(newState);
        }
    }

    private NavigationActivityState calculateNavigationActivityState() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("Current state: current activity = '%s', free nav activity = "
                            + "'%s', focus = %s", mCurrentNavigationActivity,
                    mFreeNavigationActivity,
                    mNavigationFocus.getValue()));
        }
        if (mNavigationFocus.getValue() != null && mNavigationFocus.getValue()) {
            // Car service controls which activity is displayed while driving, so we assume this
            // has already been taken care of.
            return NavigationActivityState.VISIBLE;
        } else if (mFreeNavigationActivity == null) {
            return NavigationActivityState.NOT_SELECTED;
        } else if (Objects.equals(mFreeNavigationActivity, mCurrentNavigationActivity)) {
            return NavigationActivityState.VISIBLE;
        } else {
            return NavigationActivityState.LOADING;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> MutableLiveData<T> getSensorMutableLiveData(Sensor<T> sensor) {
        return (MutableLiveData<T>) mSensorLiveDatas
                .computeIfAbsent(sensor, x -> new MutableLiveData<>());
    }
}
