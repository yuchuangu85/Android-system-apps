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

package android.car.apitest;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

/**
 * Base class to test {@link CarPropertyConfig} and {@link CarPropertyValue}.
 */
public class CarPropertyTestBase extends AndroidTestCase {

    protected static final int FLOAT_PROPERTY_ID        = 0x1160BEEF;
    protected static final int INT_ARRAY_PROPERTY_ID    = 0x0041BEEF;
    protected static final int INT_PROPERTY_ID          = 0x0040BEFF;
    protected static final int LONG_PROPERTY_ID         = 0x0050BEFF;

    protected static final int CAR_AREA_TYPE    = 0xDEADBEEF;
    protected static final int WINDOW_DRIVER    = 0x00000001;
    protected static final int WINDOW_PASSENGER = 0x00000002;

    private Parcel mParcel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mParcel = Parcel.obtain();
    }

    @Override
    protected void tearDown() throws Exception {
        mParcel.recycle();
        super.tearDown();
    }

    protected  <T extends Parcelable> T readFromParcel() {
        mParcel.setDataPosition(0);
        return mParcel.readParcelable(null);
    }

    protected void writeToParcel(Parcelable value) {
        mParcel.writeParcelable(value, 0);
    }
}
