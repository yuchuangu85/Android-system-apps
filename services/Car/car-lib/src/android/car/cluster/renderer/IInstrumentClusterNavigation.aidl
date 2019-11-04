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
package android.car.cluster.renderer;

import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

/**
 * Binder API for Instrument Cluster Navigation. This represents a direct communication channel
 * from navigation applications to the cluster vendor implementation.
 *
 * @hide
 */
interface IInstrumentClusterNavigation {
    /**
     * Called when there is a change on the navigation state.
     *
     * @param bundle {@link android.os.Bundle} containing the description of the navigation state
     *               change. This information can be parsed using
     *               <a href="https://developer.android.com/reference/androidx/car/cluster/navigation/NavigationState.html#toParcelable()">
     *               androidx.car.cluster.navigation.NavigationState#fromParcelable(Parcelable)</a>
     */
    void onNavigationStateChanged(in Bundle bundle);

    /**
     * Returns attributes of instrument cluster for navigation.
     */
    CarNavigationInstrumentCluster getInstrumentClusterInfo();
}
