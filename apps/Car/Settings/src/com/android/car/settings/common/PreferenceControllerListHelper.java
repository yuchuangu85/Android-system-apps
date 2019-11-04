/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.android.car.settings.common.PreferenceXmlParser.METADATA_CONTROLLER;
import static com.android.car.settings.common.PreferenceXmlParser.METADATA_KEY;

import android.annotation.NonNull;
import android.annotation.XmlRes;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper to load {@link PreferenceController} instances from XML. Based on com.android
 * .settings.core.PreferenceControllerListHelper.
 */
class PreferenceControllerListHelper {
    private PreferenceControllerListHelper() {
    }

    /**
     * Creates a list of {@link PreferenceController}.
     *
     * @param context the {@link Context} used to instantiate the controllers.
     * @param xmlResId the XML resource containing the metadata of the controllers to
     *         create.
     * @param fragmentController a valid {@link FragmentController} the preference
     *         controllers can use to navigate.
     * @param uxRestrictions the current {@link CarUxRestrictions}.
     * @throws IllegalArgumentException if the XML resource cannot be parsed, if the XML
     *         resource contains elements which declare controllers without preference keys, if the
     *         XML resource contains controllers which cannot be instantiated successfully.
     */
    @NonNull
    static List<PreferenceController> getPreferenceControllersFromXml(Context context,
            @XmlRes int xmlResId, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        List<PreferenceController> controllers = new ArrayList<>();
        List<Bundle> preferenceMetadata;
        try {
            preferenceMetadata = PreferenceXmlParser.extractMetadata(context, xmlResId,
                    PreferenceXmlParser.MetadataFlag.FLAG_NEED_KEY
                            | PreferenceXmlParser.MetadataFlag.FLAG_NEED_PREF_CONTROLLER);
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalArgumentException(
                    "Failed to parse preference XML for getting controllers", e);
        }

        for (Bundle metadata : preferenceMetadata) {
            String controllerName = metadata.getString(METADATA_CONTROLLER);
            if (TextUtils.isEmpty(controllerName)) {
                continue; // Preference does not require a controller.
            }
            String key = metadata.getString(METADATA_KEY);
            if (TextUtils.isEmpty(key)) {
                throw new IllegalArgumentException("Missing key for controller: " + controllerName);
            }
            controllers.add(createInstance(controllerName, context, key, fragmentController,
                    uxRestrictions));
        }

        return controllers;
    }

    private static PreferenceController createInstance(String controllerName,
            Context context, String key, FragmentController fragmentController,
            CarUxRestrictions restrictionInfo) {
        try {
            Class<?> clazz = Class.forName(controllerName);
            Constructor<?> preferenceConstructor = clazz.getConstructor(Context.class, String.class,
                    FragmentController.class, CarUxRestrictions.class);
            Object[] params = new Object[]{context, key, fragmentController, restrictionInfo};
            return (PreferenceController) preferenceConstructor.newInstance(params);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Invalid preference controller: " + controllerName, e);
        }
    }
}

