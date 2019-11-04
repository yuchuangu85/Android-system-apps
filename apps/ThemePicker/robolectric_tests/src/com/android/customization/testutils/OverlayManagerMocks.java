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
package com.android.customization.testutils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.om.OverlayManager;
import android.text.TextUtils;

import com.android.customization.model.theme.OverlayManagerCompat;

import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class to provide mock implementation for OverlayManager, to use, create a Mockito Mock
 * for OverlayManager and call {@link #setUpMock(OverlayManager)} with it, then use
 * {@link #addOverlay(String, String, String, boolean, int)} to add fake OverlayInfo to be returned
 * by the mocked OverlayManager.
 */
public class OverlayManagerMocks {
    private static class MockOverlay {
        final String packageName;
        final String targetPackage;
        final String category;

        public MockOverlay(String packageName, String targetPackage, String category) {
            this.packageName = packageName;
            this.targetPackage = targetPackage;
            this.category = category;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MockOverlay
                    && TextUtils.equals(((MockOverlay) obj).packageName, packageName)
                    && TextUtils.equals(((MockOverlay) obj).targetPackage, targetPackage)
                    && TextUtils.equals(((MockOverlay) obj).category, category);
        }
    }

    private Set<MockOverlay> mAllOverlays = new HashSet<>();
    private Set<MockOverlay> mEnabledOverlays = new HashSet<>();

    private boolean setEnabled(String packageName, boolean enable, int userId) {
        if (packageName == null) {
            return false;
        }
        Set<MockOverlay> packageOverlays = mAllOverlays.stream()
                .filter(mockOverlay -> mockOverlay.packageName.equals(packageName)).collect(
                Collectors.toSet());;
        if (packageOverlays.isEmpty()) {
            return false;
        }
        if (enable) {
            mEnabledOverlays.addAll(packageOverlays);
        } else {
            mEnabledOverlays.removeAll(packageOverlays);
        }
        return true;
    }

    public void addOverlay(String packageName, String targetPackage, String category,
            boolean enabled, int userId) {
        MockOverlay overlay = new MockOverlay(packageName, targetPackage, category);
        mAllOverlays.add(overlay);
        if (enabled) {
            mEnabledOverlays.add(overlay);
        }
    }

    public void clearOverlays() {
        mAllOverlays.clear();
        mEnabledOverlays.clear();
    }

    public void setUpMock(OverlayManagerCompat mockOverlayManager) {
        when(mockOverlayManager.getEnabledPackageName(anyString(), anyString())).then(
                (Answer<String>) inv ->
                        mEnabledOverlays.stream().filter(
                                mockOverlay ->
                                        mockOverlay.targetPackage.equals(inv.getArgument(0))
                                            && mockOverlay.category.equals(inv.getArgument(1)))
                                .map(overlay -> overlay.packageName).findFirst().orElse(null));


        when(mockOverlayManager.disableOverlay(anyString(), anyInt())).then(
                (Answer<Boolean>) invocation ->
                        setEnabled(invocation.getArgument(0),
                                false,
                                invocation.getArgument(1)));

        when(mockOverlayManager.setEnabledExclusiveInCategory(anyString(), anyInt())).then(
                (Answer<Boolean>) invocation ->
                        setEnabled(
                                invocation.getArgument(0),
                                true,
                                invocation.getArgument(1)));

        when(mockOverlayManager.getEnabledOverlaysForTargets(any())).then(
                (Answer<Map<String, String>>) inv ->
                        mEnabledOverlays.stream().filter(
                                overlay ->
                                        Arrays.asList(inv.getArguments())
                                                .contains(overlay.targetPackage))
                                .collect(Collectors.toMap(
                                        overlay ->
                                                overlay.category,
                                        (Function<MockOverlay, String>) overlay ->
                                                overlay.packageName))
        );
    }
}