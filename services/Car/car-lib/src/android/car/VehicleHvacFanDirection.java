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
package android.car;

/**
 * Bit flags for fan direction.
 * This constant must be used with HVAC_FAN_DIRECTION property in {@link VehiclePropertyIds}.
 * @hide
 */
public final class VehicleHvacFanDirection {
    public static final int FACE = 0x1;
    public static final int FLOOR = 0x2;
    public static final int DEFROST = 0x4;

    private VehicleHvacFanDirection() {}
}
