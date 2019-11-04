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

package com.android.tv.tunerinputcontroller;

import android.content.Context;
import android.content.Intent;

/** Controls the package visibility of built in tuner services. */
public interface TunerInputController {

    Intent createSetupIntent(Context context);

    void onCheckingUsbTunerStatus(Context context, String action);

    void executeNetworkTunerDiscoveryAsyncTask(Context context);

    /**
     * Updates tuner input's info.
     *
     * @param context {@link Context} instance
     */
    void updateTunerInputInfo(Context context);
}
