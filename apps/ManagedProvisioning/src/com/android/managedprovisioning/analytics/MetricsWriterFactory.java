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
 * limitations under the License
 */

package com.android.managedprovisioning.analytics;

import android.content.Context;

import com.android.internal.util.Preconditions;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.provisioning.Constants;

/**
 * Factory which determines what {@link MetricsWriter} to use.
 */
public class MetricsWriterFactory {
    /**
     * Returns the appropriate {@link MetricsWriter}.
     *
     * <p>As we want to send statsd logs only after user has consented to the relevant
     * consent screens, we defer the metrics until the end of the in-setup wizard provisioning flow.
     * Statsd then uploads the logs if the user has consented.
     *
     * <p>If provisioning happens post-setup wizard, we send the logs to statsd right away.
     */
    public static MetricsWriter getMetricsWriter(Context context, SettingsFacade settingsFacade) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(settingsFacade);
        if (settingsFacade.isDuringSetupWizard(context)) {
            return new DeferredMetricsWriter(Constants.getDeferredMetricsFile(context));

        }
        return new InstantMetricsWriter();
    }
}
