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
package com.android.tv.tuner.api;

import android.support.annotation.IntDef;
import android.support.annotation.StringDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A interface a hardware tuner device. */
public interface Tuner extends AutoCloseable {

    int FILTER_TYPE_OTHER = 0;
    int FILTER_TYPE_AUDIO = 1;
    int FILTER_TYPE_VIDEO = 2;
    int FILTER_TYPE_PCR = 3;
    String MODULATION_8VSB = "8VSB";
    String MODULATION_QAM256 = "QAM256";
    int DELIVERY_SYSTEM_UNDEFINED = 0;
    int DELIVERY_SYSTEM_ATSC = 1;
    int DELIVERY_SYSTEM_DVBC = 2;
    int DELIVERY_SYSTEM_DVBS = 3;
    int DELIVERY_SYSTEM_DVBS2 = 4;
    int DELIVERY_SYSTEM_DVBT = 5;
    int DELIVERY_SYSTEM_DVBT2 = 6;
    int TUNER_TYPE_BUILT_IN = 1;
    int TUNER_TYPE_USB = 2;
    int TUNER_TYPE_NETWORK = 3;
    int BUILT_IN_TUNER_TYPE_LINUX_DVB = 1;

    /** Check a delivery system is for DVB or not. */
    static boolean isDvbDeliverySystem(@DeliverySystemType int deliverySystemType) {
        return deliverySystemType == DELIVERY_SYSTEM_DVBC
                || deliverySystemType == DELIVERY_SYSTEM_DVBS
                || deliverySystemType == DELIVERY_SYSTEM_DVBS2
                || deliverySystemType == DELIVERY_SYSTEM_DVBT
                || deliverySystemType == DELIVERY_SYSTEM_DVBT2;
    }

    boolean isReusable();

    /**
     * Acquires the first available tuner device. If there is a tuner device that is available, the
     * tuner device will be locked to the current instance.
     *
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    boolean openFirstAvailable();

    boolean isDeviceOpen();

    long getDeviceId();

    boolean tune(int frequency, @ModulationType String modulation, String channelNumber);

    boolean addPidFilter(int pid, @FilterType int filterType);

    void stopTune();

    void setHasPendingTune(boolean hasPendingTune);

    int getDeliverySystemType();

    int readTsStream(byte[] javaBuffer, int javaBufferSize);

    int getSignalStrength();

    /** Filter type */
    @IntDef({FILTER_TYPE_OTHER, FILTER_TYPE_AUDIO, FILTER_TYPE_VIDEO, FILTER_TYPE_PCR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterType {}

    /** Modulation Type */
    @StringDef({MODULATION_8VSB, MODULATION_QAM256})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ModulationType {}

    /** Delivery System Type */
    @IntDef({
        DELIVERY_SYSTEM_UNDEFINED,
        DELIVERY_SYSTEM_ATSC,
        DELIVERY_SYSTEM_DVBC,
        DELIVERY_SYSTEM_DVBS,
        DELIVERY_SYSTEM_DVBS2,
        DELIVERY_SYSTEM_DVBT,
        DELIVERY_SYSTEM_DVBT2
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeliverySystemType {}

    /** Tuner Type */
    @IntDef({TUNER_TYPE_BUILT_IN, TUNER_TYPE_USB, TUNER_TYPE_NETWORK})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TunerType {}

    /** Built in tuner type */
    @IntDef({
        BUILT_IN_TUNER_TYPE_LINUX_DVB
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BuiltInTunerType {}
}
