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

package com.android.car.settings.testutils;

import android.content.Context;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(RestrictedLockUtilsInternal.class)
public class ShadowRestrictedLockUtilsInternal {

    private static RestrictedLockUtils.EnforcedAdmin sEnforcedAdmin;
    private static boolean sHasBaseUserRestriction;

    @Resetter
    public static void reset() {
        sEnforcedAdmin = null;
        sHasBaseUserRestriction = false;
    }

    public static void setEnforcedAdmin(RestrictedLockUtils.EnforcedAdmin enforcedAdmin) {
        sEnforcedAdmin = enforcedAdmin;
    }

    public static void setHasBaseUserRestriction(boolean hasBaseUserRestriction) {
        sHasBaseUserRestriction = hasBaseUserRestriction;
    }

    public static void sendShowAdminSupportDetailsIntent(Context context,
            RestrictedLockUtils.EnforcedAdmin admin) {
        // do nothing
    }

    @Implementation
    protected static RestrictedLockUtils.EnforcedAdmin checkIfRestrictionEnforced(Context context,
            String userRestriction, int userId) {
        return sEnforcedAdmin;
    }

    @Implementation
    protected static boolean hasBaseUserRestriction(Context context,
            String userRestriction, int userId) {
        return sHasBaseUserRestriction;
    }
}
