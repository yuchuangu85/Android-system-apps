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

package com.android.car.settings.inputmethod;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.inputmethod.InputMethodAndSubtypeUtil;

import java.util.List;

/** Keyboard utility class. */
public final class InputMethodUtil {
    /**
     * Delimiter for Enabled Input Methods' concatenated string.
     */
    public static final char INPUT_METHOD_DELIMITER = ':';
    /**
     * Splitter for Enabled Input Methods' concatenated string.
     */
    public static final TextUtils.SimpleStringSplitter sInputMethodSplitter =
            new TextUtils.SimpleStringSplitter(INPUT_METHOD_DELIMITER);
    @VisibleForTesting
    static final Drawable NO_ICON = new ColorDrawable(Color.TRANSPARENT);

    private InputMethodUtil() {
    }

    /** Returns package icon. */
    public static Drawable getPackageIcon(@NonNull PackageManager packageManager,
            @NonNull InputMethodInfo inputMethodInfo) {
        Drawable icon;
        try {
            icon = packageManager.getApplicationIcon(inputMethodInfo.getPackageName());
        } catch (NameNotFoundException e) {
            icon = NO_ICON;
        }

        return icon;
    }

    /** Returns package label. */
    public static String getPackageLabel(@NonNull PackageManager packageManager,
            @NonNull InputMethodInfo inputMethodInfo) {
        return inputMethodInfo.loadLabel(packageManager).toString();
    }

    /** Returns input method summary. */
    public static String getSummaryString(@NonNull Context context,
            @NonNull InputMethodManager inputMethodManager,
            @NonNull InputMethodInfo inputMethodInfo) {
        List<InputMethodSubtype> subtypes =
                inputMethodManager.getEnabledInputMethodSubtypeList(
                        inputMethodInfo, /* allowsImplicitlySelectedSubtypes= */ true);
        return InputMethodAndSubtypeUtil.getSubtypeLocaleNameListAsSentence(
                subtypes, context, inputMethodInfo);
    }

    /**
     * Check if input method is enabled.
     *
     * @return {@code true} if the input method is enabled.
     */
    public static boolean isInputMethodEnabled(ContentResolver resolver,
            InputMethodInfo inputMethodInfo) {
        sInputMethodSplitter.setString(getEnabledInputMethodsConcatenatedIds(resolver));
        while (sInputMethodSplitter.hasNext()) {
            String inputMethodId = sInputMethodSplitter.next();
            if (inputMethodId.equals(inputMethodInfo.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enable an input method using its InputMethodInfo.
     */
    public static void enableInputMethod(ContentResolver resolver,
            InputMethodInfo inputMethodInfo) {
        if (isInputMethodEnabled(resolver, inputMethodInfo)) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(getEnabledInputMethodsConcatenatedIds(resolver));

        if (!builder.toString().isEmpty()) {
            builder.append(INPUT_METHOD_DELIMITER);
        }

        builder.append(inputMethodInfo.getId());

        setEnabledInputMethodsConcatenatedIds(resolver, builder.toString());
    }

    /**
     * Disable an input method if its not the default system input method or if there exists another
     * enabled input method that can also be set as the default system input method.
     */
    public static void disableInputMethod(Context context, InputMethodManager inputMethodManager,
            InputMethodInfo inputMethodInfo) {
        List<InputMethodInfo> enabledInputMethodInfos = inputMethodManager
                .getEnabledInputMethodList();
        StringBuilder builder = new StringBuilder();

        boolean foundAnotherEnabledDefaultInputMethod = false;
        boolean isSystemDefault = isDefaultInputMethod(context.getContentResolver(),
                inputMethodInfo);
        for (InputMethodInfo enabledInputMethodInfo : enabledInputMethodInfos) {
            if (enabledInputMethodInfo.getId().equals(inputMethodInfo.getId())) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(INPUT_METHOD_DELIMITER);
            }

            builder.append(enabledInputMethodInfo.getId());

            if (isSystemDefault && enabledInputMethodInfo.isDefault(context)) {
                foundAnotherEnabledDefaultInputMethod = true;
                setDefaultInputMethodId(context.getContentResolver(),
                        enabledInputMethodInfo.getId());
            }
        }

        if (isSystemDefault && !foundAnotherEnabledDefaultInputMethod) {
            return;
        }

        setEnabledInputMethodsConcatenatedIds(context.getContentResolver(), builder.toString());
    }

    private static String getEnabledInputMethodsConcatenatedIds(ContentResolver resolver) {
        return Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS);
    }

    private static String getDefaultInputMethodId(ContentResolver resolver) {
        return Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    private static boolean isDefaultInputMethod(ContentResolver resolver,
            InputMethodInfo inputMethodInfo) {
        return inputMethodInfo.getId().equals(getDefaultInputMethodId(resolver));
    }

    private static void setEnabledInputMethodsConcatenatedIds(ContentResolver resolver,
            String enabledInputMethodIds) {
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_INPUT_METHODS,
                enabledInputMethodIds);
    }

    private static void setDefaultInputMethodId(ContentResolver resolver,
            String defaultInputMethodId) {
        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD,
                defaultInputMethodId);
    }
}
