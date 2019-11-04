/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner;

import android.content.Context;
import android.util.Log;
import com.android.tv.common.BuildConfig;
import com.android.tv.common.compat.TvInputConstantCompat;
import com.android.tv.tuner.api.Tuner;
import com.android.tv.common.annotation.UsedByNative;
import java.util.Objects;

/** A base class to handle a hardware tuner device. */
public abstract class TunerHal implements Tuner {
    private static final String TAG = "TunerHal";

    private static final int PID_PAT = 0;
    private static final int PID_ATSC_SI_BASE = 0x1ffb;
    private static final int PID_DVB_SDT = 0x0011;
    private static final int PID_DVB_EIT = 0x0012;
    private static final int DEFAULT_VSB_TUNE_TIMEOUT_MS = 2000;
    private static final int DEFAULT_QAM_TUNE_TIMEOUT_MS = 4000; // Some device takes time for

    @DeliverySystemType private int mDeliverySystemType;
    private boolean mIsStreaming;
    private int mFrequency;
    private String mModulation;

    static {
        if (!BuildConfig.NO_JNI_TEST) {
            System.loadLibrary("tunertvinput_jni");
        }
    }

    protected TunerHal(Context context) {
        mIsStreaming = false;
        mFrequency = -1;
        mModulation = null;
    }

    protected boolean isStreaming() {
        return mIsStreaming;
    }

    protected void getDeliverySystemTypeFromDevice() {
        if (mDeliverySystemType == DELIVERY_SYSTEM_UNDEFINED) {
            mDeliverySystemType = nativeGetDeliverySystemType(getDeviceId());
        }
    }

    /**
     * Returns {@code true} if this tuner HAL can be reused to save tuning time between channels of
     * the same frequency.
     */
    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    protected native void nativeFinalize(long deviceId);

    /**
     * Sets the tuner channel. This should be called after acquiring a tuner device.
     *
     * @param frequency a frequency of the channel to tune to
     * @param modulation a modulation method of the channel to tune to
     * @param channelNumber channel number when channel number is already known. Some tuner HAL may
     *     use channelNumber instead of frequency for tune.
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    @Override
    public synchronized boolean tune(
            int frequency, @ModulationType String modulation, String channelNumber) {
        if (!isDeviceOpen()) {
            Log.e(TAG, "There's no available device");
            return false;
        }
        if (mIsStreaming) {
            nativeCloseAllPidFilters(getDeviceId());
            mIsStreaming = false;
        }

        // When tuning to a new channel in the same frequency, there's no need to stop current tuner
        // device completely and the only thing necessary for tuning is reopening pid filters.
        if (mFrequency == frequency && Objects.equals(mModulation, modulation)) {
            addPidFilter(PID_PAT, FILTER_TYPE_OTHER);
            addPidFilter(PID_ATSC_SI_BASE, FILTER_TYPE_OTHER);
            if (Tuner.isDvbDeliverySystem(mDeliverySystemType)) {
                addPidFilter(PID_DVB_SDT, FILTER_TYPE_OTHER);
                addPidFilter(PID_DVB_EIT, FILTER_TYPE_OTHER);
            }
            mIsStreaming = true;
            return true;
        }
        int timeout_ms =
                modulation.equals(MODULATION_8VSB)
                        ? DEFAULT_VSB_TUNE_TIMEOUT_MS
                        : DEFAULT_QAM_TUNE_TIMEOUT_MS;
        if (nativeTune(getDeviceId(), frequency, modulation, timeout_ms)) {
            addPidFilter(PID_PAT, FILTER_TYPE_OTHER);
            addPidFilter(PID_ATSC_SI_BASE, FILTER_TYPE_OTHER);
            if (Tuner.isDvbDeliverySystem(mDeliverySystemType)) {
                addPidFilter(PID_DVB_SDT, FILTER_TYPE_OTHER);
                addPidFilter(PID_DVB_EIT, FILTER_TYPE_OTHER);
            }
            mFrequency = frequency;
            mModulation = modulation;
            mIsStreaming = true;
            return true;
        }
        return false;
    }

    protected native boolean nativeTune(
            long deviceId, int frequency, @ModulationType String modulation, int timeout_ms);

    /**
     * Sets a pid filter. This should be set after setting a channel.
     *
     * @param pid a pid number to be added to filter list
     * @param filterType a type of pid. Must be one of (FILTER_TYPE_XXX)
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    @Override
    public synchronized boolean addPidFilter(int pid, @FilterType int filterType) {
        if (!isDeviceOpen()) {
            Log.e(TAG, "There's no available device");
            return false;
        }
        if (pid >= 0 && pid <= 0x1fff) {
            nativeAddPidFilter(getDeviceId(), pid, filterType);
            return true;
        }
        return false;
    }

    protected native void nativeAddPidFilter(long deviceId, int pid, @FilterType int filterType);

    protected native void nativeCloseAllPidFilters(long deviceId);

    protected native void nativeSetHasPendingTune(long deviceId, boolean hasPendingTune);

    protected native int nativeGetDeliverySystemType(long deviceId);

    protected native int nativeGetSignalStrength(long deviceId);

    /**
     * Stops current tuning. The tuner device and pid filters will be reset by this call and make
     * the tuner ready to accept another tune request.
     */
    @Override
    public synchronized void stopTune() {
        if (isDeviceOpen()) {
            if (mIsStreaming) {
                nativeCloseAllPidFilters(getDeviceId());
            }
            nativeStopTune(getDeviceId());
        }
        mIsStreaming = false;
        mFrequency = -1;
        mModulation = null;
    }

    @Override
    public void setHasPendingTune(boolean hasPendingTune) {
        nativeSetHasPendingTune(getDeviceId(), hasPendingTune);
    }

    @Override
    public int getDeliverySystemType() {
        return mDeliverySystemType;
    }

    protected native void nativeStopTune(long deviceId);

    /**
     * This method must be called after {@link #tune(int, String, String)} and before {@link
     * #stopTune()}. Writes at most maxSize TS frames in a buffer provided by the user. The frames
     * employ MPEG encoding.
     *
     * @param javaBuffer a buffer to write the video data in
     * @param javaBufferSize the max amount of bytes to write in this buffer. Usually this number
     *     should be equal to the length of the buffer.
     * @return the amount of bytes written in the buffer. Note that this value could be 0 if no new
     *     frames have been obtained since the last call.
     */
    @Override
    public synchronized int readTsStream(byte[] javaBuffer, int javaBufferSize) {
        if (isDeviceOpen()) {
            return nativeWriteInBuffer(getDeviceId(), javaBuffer, javaBufferSize);
        } else {
            return 0;
        }
    }

    /**
     * This method gets signal strength for currently tuned channel.
     * Each specific tuner should implement its own method.
     *
     * @return {@link TvInputConstantCompat#SIGNAL_STRENGTH_NOT_USED
     *          when signal check is not supported from tuner.
     *          {@link TvInputConstantCompat#SIGNAL_STRENGTH_ERROR}
     *          when signal returned is not valid.
     *          0 - 100 representing strength from low to high. Curve raw data if necessary.
     */
    @Override
    public int getSignalStrength() {
        return TvInputConstantCompat.SIGNAL_STRENGTH_NOT_USED;
    }

    protected native int nativeWriteInBuffer(long deviceId, byte[] javaBuffer, int javaBufferSize);

    /**
     * Opens Linux DVB frontend device. This method is called from native JNI and used only for
     * DvbTunerHal.
     */
    @UsedByNative("DvbManager.cpp")
    protected int openDvbFrontEndFd() {
        return -1;
    }

    /**
     * Opens Linux DVB demux device. This method is called from native JNI and used only for
     * DvbTunerHal.
     */
    @UsedByNative("DvbManager.cpp")
    protected int openDvbDemuxFd() {
        return -1;
    }

    /**
     * Opens Linux DVB dvr device. This method is called from native JNI and used only for
     * DvbTunerHal.
     */
    @UsedByNative("DvbManager.cpp")
    protected int openDvbDvrFd() {
        return -1;
    }
}
