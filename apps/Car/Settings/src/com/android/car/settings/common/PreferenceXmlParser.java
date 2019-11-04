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

package com.android.car.settings.common;

import android.annotation.NonNull;
import android.annotation.XmlRes;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.annotation.IntDef;

import com.android.car.settings.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class to parse elements of XML preferences. This is a reduced version of {@code com
 * .android.settings.core.PreferenceXmlParserUtils}.
 */
public class PreferenceXmlParser {

    private static final Logger LOG = new Logger(PreferenceXmlParser.class);

    private static final String PREF_TAG_ENDS_WITH = "Preference";
    private static final String PREF_GROUP_TAG_ENDS_WITH = "PreferenceGroup";
    private static final List<String> SUPPORTED_PREF_TYPES = Arrays.asList("Preference",
            "PreferenceCategory", "PreferenceScreen");

    /**
     * Flag definition to indicate which metadata should be extracted when
     * {@link #extractMetadata(Context, int, int)} is called. The flags can be combined by using |
     * (binary or).
     */
    @IntDef(flag = true, value = {
            MetadataFlag.FLAG_NEED_KEY,
            MetadataFlag.FLAG_NEED_PREF_CONTROLLER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MetadataFlag {
        int FLAG_NEED_KEY = 1;
        int FLAG_NEED_PREF_CONTROLLER = 1 << 1;
    }

    static final String METADATA_KEY = "key";
    static final String METADATA_CONTROLLER = "controller";

    /**
     * Extracts metadata from each preference XML and puts them into a {@link Bundle}.
     *
     * @param xmlResId xml res id of a preference screen
     * @param flags one or more of {@link MetadataFlag}
     * @return a list of Bundles containing the extracted metadata
     */
    @NonNull
    public static List<Bundle> extractMetadata(Context context, @XmlRes int xmlResId, int flags)
            throws IOException, XmlPullParserException {
        final List<Bundle> metadata = new ArrayList<>();
        if (xmlResId <= 0) {
            LOG.d(xmlResId + " is invalid.");
            return metadata;
        }
        final XmlResourceParser parser = context.getResources().getXml(xmlResId);

        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && type != XmlPullParser.START_TAG) {
            // Parse next until start tag is found
        }
        final int outerDepth = parser.getDepth();

        do {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            final String nodeName = parser.getName();
            if (!SUPPORTED_PREF_TYPES.contains(nodeName) && !nodeName.endsWith(PREF_TAG_ENDS_WITH)
                    && !nodeName.endsWith(PREF_GROUP_TAG_ENDS_WITH)) {
                continue;
            }
            final Bundle preferenceMetadata = new Bundle();
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            final TypedArray preferenceAttributes = context.obtainStyledAttributes(attrs,
                    R.styleable.Preference);

            if (hasFlag(flags, MetadataFlag.FLAG_NEED_KEY)) {
                preferenceMetadata.putString(METADATA_KEY, getKey(preferenceAttributes));
            }
            if (hasFlag(flags, MetadataFlag.FLAG_NEED_PREF_CONTROLLER)) {
                preferenceMetadata.putString(METADATA_CONTROLLER,
                        getController(preferenceAttributes));
            }
            metadata.add(preferenceMetadata);

            preferenceAttributes.recycle();
        } while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth));
        parser.close();

        return metadata;
    }

    private static boolean hasFlag(int flags, @MetadataFlag int flag) {
        return (flags & flag) != 0;
    }

    private static String getKey(TypedArray styledAttributes) {
        return styledAttributes.getString(com.android.internal.R.styleable.Preference_key);
    }

    private static String getController(TypedArray styledAttributes) {
        return styledAttributes.getString(R.styleable.Preference_controller);
    }
}
