/*
 * Copyright 2019 The Android Open Source Project
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

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.util.ArrayMap;
import android.util.ArraySet;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Map;
import java.util.Set;

@Implements(NotificationManager.class)
public class ShadowNotificationManager extends org.robolectric.shadows.ShadowNotificationManager {

    private Set<String> mNotificationPolicyGrantedPackages = new ArraySet<>();
    private Set<ComponentName> mNotificationListenerAccessGrantedComponents = new ArraySet<>();
    private Map<String, AutomaticZenRule> mAutomaticZenRules = new ArrayMap<>();
    private int mZenRuleIdCounter = 0;

    @Implementation
    public void setNotificationPolicyAccessGranted(String pkg, boolean granted) {
        if (granted) {
            mNotificationPolicyGrantedPackages.add(pkg);
        } else {
            mNotificationPolicyGrantedPackages.remove(pkg);
        }
    }

    @Implementation
    protected boolean isNotificationPolicyAccessGrantedForPackage(String pkg) {
        return mNotificationPolicyGrantedPackages.contains(pkg);
    }

    @Implementation
    protected boolean isNotificationListenerAccessGranted(ComponentName listener) {
        return mNotificationListenerAccessGrantedComponents.contains(listener);
    }

    @Implementation
    protected void setNotificationListenerAccessGranted(ComponentName listener, boolean granted) {
        if (granted) {
            mNotificationListenerAccessGrantedComponents.add(listener);
        } else {
            mNotificationListenerAccessGrantedComponents.remove(listener);
        }
    }

    @Implementation
    protected Map<String, AutomaticZenRule> getAutomaticZenRules() {
        return mAutomaticZenRules;
    }

    @Implementation
    protected String addAutomaticZenRule(AutomaticZenRule automaticZenRule) {
        String id = String.valueOf(mZenRuleIdCounter++);
        mAutomaticZenRules.put(id, automaticZenRule);
        return id;
    }

    @Implementation
    protected void removeAutomaticZenRules(String packageName) {
        mAutomaticZenRules.clear();
    }
}
