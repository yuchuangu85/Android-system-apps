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
package android.car.userlib;

import android.os.UserManager;
import android.sysprop.CarProperties;

/**
 * Testable versions of APIs from the framework.  This helps test difficult areas of the framework
 * due to limitations like SELinux or hard to mock static methods.
 */
public class TestableFrameworkWrapper {
    /**
     * Wrapper around {@link CarProperties#boot_user_override_id()}
     */
    public int getBootUserOverrideId(int defaultValue) {
        return CarProperties.boot_user_override_id().orElse(defaultValue);
    }

    /**
     * Wrapper around {@link UserManager#getMaxSupportedUsers()}
     */
    public int userManagerGetMaxSupportedUsers() {
        return UserManager.getMaxSupportedUsers();
    }
}
