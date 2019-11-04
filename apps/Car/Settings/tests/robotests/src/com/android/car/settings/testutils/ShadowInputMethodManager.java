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

import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Nullable;

import com.android.car.settings.inputmethod.InputMethodUtil;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Implements(value = InputMethodManager.class)
public class ShadowInputMethodManager extends org.robolectric.shadows.ShadowInputMethodManager {
    private static List<InputMethodSubtype> sInputMethodSubtypes = new ArrayList<>();
    private static List<InputMethodInfo> sInputMethodList = new ArrayList<>();
    private static Map<String, InputMethodInfo> sInputMethodMap = new HashMap<>();

    @Resetter
    public static void reset() {
        sInputMethodSubtypes.clear();
        sInputMethodList.clear();
        sInputMethodMap.clear();
        org.robolectric.shadows.ShadowInputMethodManager.reset();
    }

    public static void setEnabledInputMethodSubtypeList(List<InputMethodSubtype> list) {
        sInputMethodSubtypes = list;
    }

    @Implementation
    protected List<InputMethodSubtype> getEnabledInputMethodSubtypeList(InputMethodInfo imi,
            boolean allowsImplicitlySelectedSubtypes) {
        return sInputMethodSubtypes;
    }

    public static void setEnabledInputMethodList(@Nullable List<InputMethodInfo> list) {
        if (list == null || list.size() == 0) {
            Settings.Secure.putString(RuntimeEnvironment.application.getContentResolver(),
                    Settings.Secure.ENABLED_INPUT_METHODS, "");
            return;
        }

        String concatenatedInputMethodIds = createInputMethodIdString(list.stream().map(
                imi -> imi.getId()).collect(Collectors.toList()).toArray(new String[list.size()]));
        Settings.Secure.putString(RuntimeEnvironment.application.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, concatenatedInputMethodIds);
        addInputMethodInfosToMap(list);
    }

    @Implementation
    protected List<InputMethodInfo> getEnabledInputMethodList() {
        List<InputMethodInfo> enabledInputMethodList = new ArrayList<>();

        String inputMethodIdString = Settings.Secure.getString(
                RuntimeEnvironment.application.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);
        if (inputMethodIdString == null || inputMethodIdString.isEmpty()) {
            return enabledInputMethodList;
        }

        InputMethodUtil.sInputMethodSplitter.setString(inputMethodIdString);
        while (InputMethodUtil.sInputMethodSplitter.hasNext()) {
            enabledInputMethodList.add(sInputMethodMap.get(InputMethodUtil.sInputMethodSplitter
                    .next()));
        }
        return enabledInputMethodList;
    }

    public void setInputMethodList(List<InputMethodInfo> inputMethodInfos) {
        sInputMethodList = inputMethodInfos;
        if (inputMethodInfos == null) {
            return;
        }

        addInputMethodInfosToMap(inputMethodInfos);
    }

    @Implementation
    protected List<InputMethodInfo> getInputMethodList() {
        return sInputMethodList;
    }

    private static String createInputMethodIdString(String... ids) {
        int size = ids == null ? 0 : ids.length;

        if (size == 1) {
            return ids[0];
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            builder.append(ids[i]);
            if (i != size - 1) {
                builder.append(InputMethodUtil.INPUT_METHOD_DELIMITER);
            }
        }
        return builder.toString();
    }

    private static void addInputMethodInfosToMap(List<InputMethodInfo> inputMethodInfos) {
        if (sInputMethodMap == null || sInputMethodMap.size() == 0) {
            sInputMethodMap = inputMethodInfos.stream().collect(Collectors.toMap(
                    InputMethodInfo::getId, imi -> imi));
            return;
        }

        inputMethodInfos.forEach(imi -> {
            sInputMethodMap.put(imi.getId(), imi);
        });
    }
}
