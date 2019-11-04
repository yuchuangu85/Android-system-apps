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
package com.android.car.storagemonitoring;

import android.annotation.Nullable;
import android.annotation.TestApi;
import android.hardware.health.V2_0.IHealth;
import android.hardware.health.V2_0.Result;
import android.hardware.health.V2_0.StorageInfo;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableInt;

import com.android.car.CarLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Loads wear information from the Health service.
 */
public class HealthServiceWearInfoProvider implements WearInformationProvider {

    private static final String INSTANCE_HEALTHD = "backup";
    private static final String INSTANCE_VENDOR = "default";

    private static final List<String> sAllInstances =
                Arrays.asList(INSTANCE_VENDOR, INSTANCE_HEALTHD);

    private IHealthSupplier mHealthSupplier;

    public HealthServiceWearInfoProvider() {
        mHealthSupplier = new IHealthSupplier() {};
    }

    @Nullable
    @Override
    public WearInformation load() {
        IHealth healthService = getHealthService();
        final MutableInt success = new MutableInt(Result.NOT_SUPPORTED);
        final MutableInt foundInternalStorageDeviceInfo = new MutableInt(0);
        final MutableInt lifetimeA = new MutableInt(0);
        final MutableInt lifetimeB = new MutableInt(0);
        final MutableInt preEol = new MutableInt(0);

        final IHealth.getStorageInfoCallback getStorageInfoCallback =
                new IHealth.getStorageInfoCallback() {
            @Override
            public void onValues(int result, ArrayList<StorageInfo> value) {
                success.value = result;
                if (result == Result.SUCCESS) {
                    int len = value.size();
                    for (int i = 0; i < len; i++) {
                        StorageInfo value2 = value.get(i);
                        if (value2.attr.isInternal) {
                            lifetimeA.value = value2.lifetimeA;
                            lifetimeB.value = value2.lifetimeB;
                            preEol.value = value2.eol;
                            foundInternalStorageDeviceInfo.value = 1;
                        }
                    }
                }
            }};

        if (healthService == null) {
            Log.w(CarLog.TAG_STORAGE, "No health service is available to fetch wear information.");
            return null;
        }

        try {
            healthService.getStorageInfo(getStorageInfoCallback);
        } catch (Exception e) {
            Log.w(CarLog.TAG_STORAGE, "Failed to get storage information from"
                    + "health service, exception :" + e);
            return null;
        }

        if (success.value != Result.SUCCESS) {
            Log.w(CarLog.TAG_STORAGE, "Health service returned result :" + success.value);
            return null;
        } else if (foundInternalStorageDeviceInfo.value == 0) {
            Log.w(CarLog.TAG_STORAGE, "Failed to find storage information for"
                    + "internal storage device");
            return null;
        } else {
            return new WearInformation(lifetimeA.value, lifetimeB.value,
                                      preEol.value);
        }
    }

    @Nullable
    private IHealth getHealthService() {
        for (String name : sAllInstances) {
            IHealth newService = null;
            try {
                newService = mHealthSupplier.get(name);
            } catch (Exception e) {
                /* ignored, handled below */
            }
            if (newService != null) {
                return newService;
            }
        }
        return null;
    }

    @TestApi
    public void setHealthSupplier(IHealthSupplier healthSupplier) {
        mHealthSupplier = healthSupplier;
    }

    /**
     * Supplier of services.
     * Must not return null; throw {@link NoSuchElementException} if a service is not available.
     */
    interface IHealthSupplier {
        default IHealth get(String name) throws NoSuchElementException, RemoteException {
            return IHealth.getService(name, false /* retry */);
        }
    }
}
