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
package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.car.vehiclehal.test.VehiclePropConfigBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

@RunWith(AndroidJUnit4.class)
public class InputHalServiceTest {
    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock VehicleHal mVehicleHal;
    @Mock InputHalService.InputListener mInputListener;
    @Mock LongSupplier mUptimeSupplier;

    private static final VehiclePropConfig HW_KEY_INPUT_CONFIG =
            VehiclePropConfigBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT).build();
    private static final int DISPLAY = 42;

    private enum Key { DOWN, UP }

    private InputHalService mInputHalService;

    @Before
    public void setUp() {
        when(mUptimeSupplier.getAsLong()).thenReturn(0L);
        mInputHalService = new InputHalService(mVehicleHal, mUptimeSupplier);
        mInputHalService.init();
    }

    @After
    public void tearDown() {
        mInputHalService.release();
        mInputHalService = null;
    }

    @Test
    public void ignoresSetListener_beforeKeyInputSupported() {
        assertThat(mInputHalService.isKeyInputSupported()).isFalse();

        mInputHalService.setInputListener(mInputListener);

        mInputHalService.handleHalEvents(
                ImmutableList.of(makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_ENTER)));
        verify(mInputListener, never()).onKeyEvent(any(), anyInt());
    }

    @Test
    public void takesKeyInputProperty() {
        Set<VehiclePropConfig> offeredProps = ImmutableSet.of(
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.ABS_ACTIVE).build(),
                HW_KEY_INPUT_CONFIG,
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.CURRENT_GEAR).build());

        Collection<VehiclePropConfig> takenProps =
                mInputHalService.takeSupportedProperties(offeredProps);

        assertThat(takenProps).containsExactly(HW_KEY_INPUT_CONFIG);
        assertThat(mInputHalService.isKeyInputSupported()).isTrue();
    }

    @Test
    public void dispatchesInputEvent_single_toListener() {
        subscribeListener();

        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
    }

    @Test
    public void dispatchesInputEvent_multiple_toListener() {
        subscribeListener();

        // KeyEvents get recycled, so we can't just use ArgumentCaptor#getAllValues here.
        // We need to make a copy of the information we need at the time of the call.
        List<KeyEvent> events = new ArrayList<>();
        doAnswer(inv -> {
            KeyEvent event = inv.getArgument(0);
            events.add(event.copy());
            return null;
        }).when(mInputListener).onKeyEvent(any(), eq(DISPLAY));

        mInputHalService.handleHalEvents(
                ImmutableList.of(
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_ENTER),
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_MENU)));

        assertThat(events.get(0).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(events.get(1).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);

        events.forEach(KeyEvent::recycle);
    }

    @Test
    public void handlesRepeatedKeys() {
        subscribeListener();

        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(0L);
        assertThat(event.getDownTime()).isEqualTo(0L);
        assertThat(event.getRepeatCount()).isEqualTo(0);

        when(mUptimeSupplier.getAsLong()).thenReturn(5L);
        event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(5L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(1);

        when(mUptimeSupplier.getAsLong()).thenReturn(10L);
        event = dispatchSingleEvent(Key.UP, KeyEvent.KEYCODE_ENTER);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_UP);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(10L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(0);

        when(mUptimeSupplier.getAsLong()).thenReturn(15L);
        event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(15L);
        assertThat(event.getDownTime()).isEqualTo(15L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
    }

    /**
     * Test for handling rotary knob event.
     */
    @Test
    public void handlesRepeatedKeyWithIndents() {
        subscribeListener();
        KeyEvent event = dispatchSingleEventWithIndents(KeyEvent.KEYCODE_VOLUME_UP, 5);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_VOLUME_UP);
        assertThat(event.getEventTime()).isEqualTo(0L);
        assertThat(event.getDownTime()).isEqualTo(0L);
        assertThat(event.getRepeatCount()).isEqualTo(4);

        when(mUptimeSupplier.getAsLong()).thenReturn(5L);
        event = dispatchSingleEventWithIndents(KeyEvent.KEYCODE_VOLUME_UP, 5);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_VOLUME_UP);
        assertThat(event.getEventTime()).isEqualTo(5L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(9);
    }

    @Test
    public void handlesKeyUp_withoutKeyDown() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.UP, KeyEvent.KEYCODE_ENTER);

        assertThat(event.getEventTime()).isEqualTo(42L);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
    }

    @Test
    public void separateKeyDownEvents_areIndependent() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(27L);
        dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER);

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_MENU);

        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
    }

    private void subscribeListener() {
        mInputHalService.takeSupportedProperties(ImmutableSet.of(HW_KEY_INPUT_CONFIG));
        assertThat(mInputHalService.isKeyInputSupported()).isTrue();

        mInputHalService.setInputListener(mInputListener);
        verify(mVehicleHal).subscribeProperty(mInputHalService, VehicleProperty.HW_KEY_INPUT);
    }


    private VehiclePropValue makeKeyPropValue(Key action, int code) {
        VehiclePropValue v = new VehiclePropValue();
        v.prop = VehicleProperty.HW_KEY_INPUT;
        v.value.int32Values.add(
                (action == Key.DOWN
                        ? VehicleHwKeyInputAction.ACTION_DOWN
                        : VehicleHwKeyInputAction.ACTION_UP));
        v.value.int32Values.add(code);
        v.value.int32Values.add(DISPLAY);
        return v;
    }

    private KeyEvent dispatchSingleEvent(Key action, int code) {
        ArgumentCaptor<KeyEvent> captor = ArgumentCaptor.forClass(KeyEvent.class);
        reset(mInputListener);
        mInputHalService.handleHalEvents(ImmutableList.of(makeKeyPropValue(action, code)));
        verify(mInputListener).onKeyEvent(captor.capture(), eq(DISPLAY));
        reset(mInputListener);
        return captor.getValue();
    }

    private VehiclePropValue makeKeyPropValueWithIndents(int code, int indents) {
        VehiclePropValue v = new VehiclePropValue();
        v.prop = VehicleProperty.HW_KEY_INPUT;
        // Only Key.down can have indents.
        v.value.int32Values.add(VehicleHwKeyInputAction.ACTION_DOWN);
        v.value.int32Values.add(code);
        v.value.int32Values.add(DISPLAY);
        v.value.int32Values.add(indents);
        return v;
    }

    private KeyEvent dispatchSingleEventWithIndents(int code, int indents) {
        ArgumentCaptor<KeyEvent> captor = ArgumentCaptor.forClass(KeyEvent.class);
        reset(mInputListener);
        mInputHalService.handleHalEvents(
                ImmutableList.of(makeKeyPropValueWithIndents(code, indents)));
        verify(mInputListener).onKeyEvent(captor.capture(), eq(DISPLAY));
        reset(mInputListener);
        return captor.getValue();
    }
}