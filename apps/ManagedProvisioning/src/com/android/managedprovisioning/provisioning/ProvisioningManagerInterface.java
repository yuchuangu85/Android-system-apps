/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning;

import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Interface for communication from activities to provisioning managers.
 */
public interface ProvisioningManagerInterface {

    /**
     * Initiate a new provisioning process, unless one is already ongoing.
     *
     * @param params {@link ProvisioningParams} associated with the new provisioning process.
     */
    void maybeStartProvisioning(final ProvisioningParams params);

    /**
     * Cancel the ongoing provisioning process.
     */
    void cancelProvisioning();

    /**
     * Register a listener for updates of the provisioning progress.
     *
     * <p>Registering a listener will immediately result in the last callback being sent to the
     * listener. All callbacks will occur on the UI thread.</p>
     *
     * @param callback listener to be registered.
     */
    void registerListener(ProvisioningManagerCallback callback);

    /**
     * Unregister a listener from updates of the provisioning progress.
     *
     * @param callback listener to be unregistered.
     */
    void unregisterListener(ProvisioningManagerCallback callback);
}
