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
package com.android.car.audio;

import android.annotation.NonNull;
import android.annotation.XmlRes;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.Xml;

import com.android.car.CarLog;
import com.android.car.R;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper class loads volume groups from car_volume_groups.xml configuration into one zone.
 *
 * @deprecated This is replaced by {@link CarAudioZonesHelper}.
 */
@Deprecated
/* package */ class CarAudioZonesHelperLegacy {

    private static final String TAG_VOLUME_GROUPS = "volumeGroups";
    private static final String TAG_GROUP = "group";
    private static final String TAG_CONTEXT = "context";

    private final Context mContext;
    private final @XmlRes int mXmlConfiguration;
    private final SparseIntArray mContextToBus;
    private final SparseArray<CarAudioDeviceInfo> mBusToCarAudioDeviceInfo;

    CarAudioZonesHelperLegacy(Context context, @XmlRes int xmlConfiguration,
            @NonNull SparseArray<CarAudioDeviceInfo> busToCarAudioDeviceInfo,
            @NonNull IAudioControl audioControl) {
        mContext = context;
        mXmlConfiguration = xmlConfiguration;
        mBusToCarAudioDeviceInfo = busToCarAudioDeviceInfo;

        // Initialize context => bus mapping once.
        mContextToBus = new SparseIntArray();
        try {
            for (int contextNumber : CarAudioDynamicRouting.CONTEXT_NUMBERS) {
                mContextToBus.put(contextNumber, audioControl.getBusForContext(contextNumber));
            }
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AUDIO, "Failed to query IAudioControl HAL", e);
            e.rethrowAsRuntimeException();
        }
    }

    CarAudioZone[] loadAudioZones() {
        final CarAudioZone zone = new CarAudioZone(CarAudioManager.PRIMARY_AUDIO_ZONE,
                "Primary zone");
        for (CarVolumeGroup group : loadVolumeGroups()) {
            zone.addVolumeGroup(group);
            // Binding audio device to volume group.
            for (int contextNumber : group.getContexts()) {
                int busNumber = mContextToBus.get(contextNumber);
                group.bind(contextNumber, busNumber, mBusToCarAudioDeviceInfo.get(busNumber));
            }
        }
        return new CarAudioZone[] { zone };
    }

    /**
     * @return all {@link CarVolumeGroup} read from configuration.
     */
    private List<CarVolumeGroup> loadVolumeGroups() {
        List<CarVolumeGroup> carVolumeGroups = new ArrayList<>();
        try (XmlResourceParser parser = mContext.getResources().getXml(mXmlConfiguration)) {
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            // Traverse to the first start tag
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && type != XmlResourceParser.START_TAG) {
                // ignored
            }

            if (!TAG_VOLUME_GROUPS.equals(parser.getName())) {
                throw new RuntimeException("Meta-data does not start with volumeGroups tag");
            }
            int outerDepth = parser.getDepth();
            int id = 0;
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && (type != XmlResourceParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlResourceParser.END_TAG) {
                    continue;
                }
                if (TAG_GROUP.equals(parser.getName())) {
                    carVolumeGroups.add(parseVolumeGroup(id, attrs, parser));
                    id++;
                }
            }
        } catch (Exception e) {
            Log.e(CarLog.TAG_AUDIO, "Error parsing volume groups configuration", e);
        }
        return carVolumeGroups;
    }

    private CarVolumeGroup parseVolumeGroup(int id, AttributeSet attrs, XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        List<Integer> contexts = new ArrayList<>();
        int type;
        int innerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && (type != XmlResourceParser.END_TAG || parser.getDepth() > innerDepth)) {
            if (type == XmlResourceParser.END_TAG) {
                continue;
            }
            if (TAG_CONTEXT.equals(parser.getName())) {
                TypedArray c = mContext.getResources().obtainAttributes(
                        attrs, R.styleable.volumeGroups_context);
                contexts.add(c.getInt(R.styleable.volumeGroups_context_context, -1));
                c.recycle();
            }
        }

        return new CarVolumeGroup(mContext, CarAudioManager.PRIMARY_AUDIO_ZONE, id,
                contexts.stream().mapToInt(i -> i).filter(i -> i >= 0).toArray());
    }
}
