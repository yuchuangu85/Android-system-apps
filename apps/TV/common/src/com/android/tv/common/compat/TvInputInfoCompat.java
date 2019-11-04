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
 * limitations under the License
 */
package com.android.tv.common.compat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputService;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;

/**
 * TIF Compatibility for {@link TvInputInfo}.
 */
public class TvInputInfoCompat {
    private static final String TAG = "TvInputInfoCompat";
    private static final String ATTRIBUTE_NAMESPACE_ANDROID =
            "http://schemas.android.com/apk/res/android";
    private static final String TV_INPUT_XML_START_TAG_NAME = "tv-input";
    private static final String TV_INPUT_EXTRA_XML_START_TAG_NAME = "extra";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_NAME_AUDIO_ONLY =
            "com.android.tv.common.compat.tvinputinfocompat.audioOnly";

    private final Context mContext;
    private final TvInputInfo mTvInputInfo;
    private final boolean mAudioOnly;

    public TvInputInfoCompat(Context context, TvInputInfo tvInputInfo) {
        mContext = context;
        mTvInputInfo = tvInputInfo;
        // TODO(b/112938832): use tvInputInfo.isAudioOnly() when SDK is updated
        mAudioOnly = Boolean.parseBoolean(getExtras().get(ATTRIBUTE_NAME_AUDIO_ONLY));
    }

    public TvInputInfo getTvInputInfo() {
        return mTvInputInfo;
    }

    public boolean isAudioOnly() {
        return mAudioOnly;
    }

    public int getType() {
        return mTvInputInfo.getType();
    }

    @VisibleForTesting
    public Map<String, String> getExtras() {
        ServiceInfo si = mTvInputInfo.getServiceInfo();

        try {
            XmlPullParser parser = getXmlResourceParser();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }

            if (!TV_INPUT_XML_START_TAG_NAME.equals(parser.getName())) {
                Log.w(TAG, "Meta-data does not start with " + TV_INPUT_XML_START_TAG_NAME
                        + " tag for " + si.name);
                return Collections.emptyMap();
            }
            // <tv-input> start tag found
            Map<String, String> extras = new HashMap<>();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.END_TAG
                        && TV_INPUT_XML_START_TAG_NAME.equals(parser.getName())) {
                    // </tv-input> end tag found
                    return extras;
                }
                if (type == XmlPullParser.START_TAG
                        && TV_INPUT_EXTRA_XML_START_TAG_NAME.equals(parser.getName())) {
                    String extraName =
                            parser.getAttributeValue(ATTRIBUTE_NAMESPACE_ANDROID, ATTRIBUTE_NAME);
                    String extraValue =
                            parser.getAttributeValue(ATTRIBUTE_NAMESPACE_ANDROID, ATTRIBUTE_VALUE);
                    if (extraName != null && extraValue != null) {
                        extras.put(extraName, extraValue);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to get extras of " + mTvInputInfo.getId() , e);
        }
        return Collections.emptyMap();
    }

    @VisibleForTesting
    XmlPullParser getXmlResourceParser() {
        ServiceInfo si = mTvInputInfo.getServiceInfo();
        PackageManager pm = mContext.getPackageManager();
        return si.loadXmlMetaData(pm, TvInputService.SERVICE_META_DATA);
    }
}
