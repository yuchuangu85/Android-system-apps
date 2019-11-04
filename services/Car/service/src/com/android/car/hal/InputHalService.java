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
package com.android.car.hal;

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.HW_KEY_INPUT;

import android.hardware.automotive.vehicle.V2_0.VehicleDisplay;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.LongSupplier;

public class InputHalService extends HalServiceBase {

    public static final int DISPLAY_MAIN = VehicleDisplay.MAIN;
    public static final int DISPLAY_INSTRUMENT_CLUSTER = VehicleDisplay.INSTRUMENT_CLUSTER;
    private final VehicleHal mHal;
    /** A function to retrieve the current system uptime - replaceable for testing. */
    private final LongSupplier mUptimeSupplier;

    public interface InputListener {
        void onKeyEvent(KeyEvent event, int targetDisplay);
    }

    /** The current press state of a key. */
    private static class KeyState {
        /** The timestamp (uptimeMillis) of the last ACTION_DOWN event for this key. */
        public long mLastKeyDownTimestamp = -1;
        /** The number of ACTION_DOWN events that have been sent for this keypress. */
        public int mRepeatCount = 0;
    }

    private static final boolean DBG = false;

    @GuardedBy("this")
    private boolean mKeyInputSupported = false;

    @GuardedBy("this")
    private InputListener mListener;

    @GuardedBy("mKeyStates")
    private final SparseArray<KeyState> mKeyStates = new SparseArray<>();

    public InputHalService(VehicleHal hal) {
        this(hal, SystemClock::uptimeMillis);
    }

    @VisibleForTesting
    InputHalService(VehicleHal hal, LongSupplier uptimeSupplier) {
        mHal = hal;
        mUptimeSupplier = uptimeSupplier;
    }

    public void setInputListener(InputListener listener) {
        synchronized (this) {
            if (!mKeyInputSupported) {
                Log.w(CarLog.TAG_INPUT, "input listener set while key input not supported");
                return;
            }
            mListener = listener;
        }
        mHal.subscribeProperty(this, HW_KEY_INPUT);
    }

    public synchronized boolean isKeyInputSupported() {
        return mKeyInputSupported;
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
        synchronized (this) {
            mListener = null;
            mKeyInputSupported = false;
        }
    }

    @Override
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> supported = new LinkedList<>();
        for (VehiclePropConfig p: allProperties) {
            if (p.prop == HW_KEY_INPUT) {
                supported.add(p);
                synchronized (this) {
                    mKeyInputSupported = true;
                }
            }
        }
        return supported;
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        InputListener listener;
        synchronized (this) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(CarLog.TAG_INPUT, "Input event while listener is null");
            return;
        }
        for (VehiclePropValue v : values) {
            if (v.prop != HW_KEY_INPUT) {
                Log.e(CarLog.TAG_INPUT, "Wrong event dispatched, prop:0x" +
                        Integer.toHexString(v.prop));
                continue;
            }
            int action = (v.value.int32Values.get(0) == VehicleHwKeyInputAction.ACTION_DOWN) ?
                            KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            int code = v.value.int32Values.get(1);
            int display = v.value.int32Values.get(2);
            int indentsCount = v.value.int32Values.size() < 4 ? 1 : v.value.int32Values.get(3);
            if (DBG) {
                Log.i(CarLog.TAG_INPUT, new StringBuilder()
                                        .append("hal event code:").append(code)
                                        .append(", action:").append(action)
                                        .append(", display: ").append(display)
                                        .append(", number of indents: ").append(indentsCount)
                                        .toString());
            }
            while (indentsCount > 0) {
                indentsCount--;
                dispatchKeyEvent(listener, action, code, display);
            }
        }
    }

    private void dispatchKeyEvent(InputListener listener, int action, int code, int display) {
        long eventTime = mUptimeSupplier.getAsLong();

        long downTime;
        int repeat;

        synchronized (mKeyStates) {
            KeyState state = mKeyStates.get(code);
            if (state == null) {
                state = new KeyState();
                mKeyStates.put(code, state);
            }

            if (action == KeyEvent.ACTION_DOWN) {
                downTime = eventTime;
                repeat = state.mRepeatCount++;
                state.mLastKeyDownTimestamp = eventTime;
            } else {
                // Handle key up events without any matching down event by setting the down time to
                // the event time. This shouldn't happen in practice - keys should be pressed
                // before they can be released! - but this protects us against HAL weirdness.
                downTime =
                        (state.mLastKeyDownTimestamp == -1)
                                ? eventTime
                                : state.mLastKeyDownTimestamp;
                repeat = 0;
                state.mRepeatCount = 0;
            }
        }

        KeyEvent event = KeyEvent.obtain(
                downTime,
                eventTime,
                action,
                code,
                repeat,
                0 /* meta state */,
                0 /* deviceId */,
                0 /* scancode */,
                0 /* flags */,
                InputDevice.SOURCE_CLASS_BUTTON,
                null /* characters */);

        listener.onKeyEvent(event, display);
        event.recycle();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Input HAL*");
        writer.println("mKeyInputSupported:" + mKeyInputSupported);
    }

}
