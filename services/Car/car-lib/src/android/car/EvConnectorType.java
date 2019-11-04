/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * EvConnectorType denotes the different connectors a EV may use.
 */
public final class EvConnectorType {
    /**
     * List of EV Connector Types used in {@link CarInfoManager#getEvConnectorTypes()}.
     * Beside connector types are listed here, there are two more EvConnectorTypes.
     * The value of GBT_DC faster charging standard is 10.
     * The value of IEC_TYPE_3_AC standard is 11.
     * If a vehicle does not know the type, it will return UNKNOWN.
     * The vehicle returns OTHER when no other types apply.
     * <b>Note:</b> The connector types in Java API have different values than the ones in VHAL.
     */
    public static final int UNKNOWN = 0;
    /** Connector type SAE J1772 */
    public static final int J1772 = 1;
    /** IEC 62196 Type 2 connector */
    public static final int MENNEKES = 2;
    /** CHAdeMo fast charger connector */
    public static final int CHADEMO = 3;
    /** Combined Charging System Combo 1 */
    public static final int COMBO_1 = 4;
    /** Combined Charging System Combo 2 */
    public static final int COMBO_2 = 5;
    /** Connector of Tesla Roadster */
    public static final int TESLA_ROADSTER = 6;
    /** High Power Wall Charger of Tesla */
    public static final int TESLA_HPWC = 7;
    /** Supercharger of Tesla */
    public static final int TESLA_SUPERCHARGER = 8;
    /** GBT_AC Fast Charging Standard */
    public static final int GBT = 9;
    /**
     * Map to GBT_DC in VHAL
     * @hide
     */
    public static final int GBT_DC = 10;
    /**
     * Map to IEC_TYPE_3_AC in VHAL
     * @hide
     */
    public static final int SCAME = 11;

    /**
     * Connector type to use when no other types apply.
     */
    public static final int OTHER = 101;

    /** @hide */
    @IntDef({
        UNKNOWN,
        J1772,
        MENNEKES,
        CHADEMO,
        COMBO_1,
        COMBO_2,
        TESLA_ROADSTER,
        TESLA_HPWC,
        TESLA_SUPERCHARGER,
        GBT,
        OTHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {}

    private EvConnectorType() {}
}
