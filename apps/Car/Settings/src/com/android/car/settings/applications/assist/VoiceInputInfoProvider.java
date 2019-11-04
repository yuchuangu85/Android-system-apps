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

package com.android.car.settings.applications.assist;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionServiceInfo;
import android.speech.RecognitionService;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;

import com.android.car.settings.common.Logger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts the voice interaction services and voice recognition services and converts them into
 * {@link VoiceInteractionInfo} instances and {@link VoiceRecognitionInfo} instances.
 */
public class VoiceInputInfoProvider {

    private static final Logger LOG = new Logger(VoiceInputInfoProvider.class);
    @VisibleForTesting
    static final Intent VOICE_INTERACTION_SERVICE_TAG = new Intent(
            VoiceInteractionService.SERVICE_INTERFACE);
    @VisibleForTesting
    static final Intent VOICE_RECOGNITION_SERVICE_TAG = new Intent(
            RecognitionService.SERVICE_INTERFACE);

    private final Context mContext;
    private final Map<ComponentName, VoiceInputInfo> mComponentToInfoMap = new ArrayMap<>();
    private final List<VoiceInteractionInfo> mVoiceInteractionInfoList = new ArrayList<>();
    private final List<VoiceRecognitionInfo> mVoiceRecognitionInfoList = new ArrayList<>();
    private final Set<ComponentName> mRecognitionServiceNames = new ArraySet<>();

    public VoiceInputInfoProvider(Context context) {
        mContext = context;

        loadVoiceInteractionServices();
        loadVoiceRecognitionServices();
    }

    /**
     * Gets the list of voice interaction services represented as {@link VoiceInteractionInfo}
     * instances.
     */
    public List<VoiceInteractionInfo> getVoiceInteractionInfoList() {
        return mVoiceInteractionInfoList;
    }

    /**
     * Gets the list of voice recognition services represented as {@link VoiceRecognitionInfo}
     * instances.
     */
    public List<VoiceRecognitionInfo> getVoiceRecognitionInfoList() {
        return mVoiceRecognitionInfoList;
    }

    /**
     * Returns the appropriate {@link VoiceInteractionInfo} or {@link VoiceRecognitionInfo} based on
     * the provided {@link ComponentName}.
     *
     * @return {@link VoiceInputInfo} if it exists for the component name, null otherwise.
     */
    @Nullable
    public VoiceInputInfo getInfoForComponent(ComponentName key) {
        return mComponentToInfoMap.getOrDefault(key, null);
    }

    private void loadVoiceInteractionServices() {
        List<ResolveInfo> mAvailableVoiceInteractionServices =
                mContext.getPackageManager().queryIntentServices(VOICE_INTERACTION_SERVICE_TAG,
                        PackageManager.GET_META_DATA);

        for (ResolveInfo resolveInfo : mAvailableVoiceInteractionServices) {
            VoiceInteractionServiceInfo interactionServiceInfo = new VoiceInteractionServiceInfo(
                    mContext.getPackageManager(), resolveInfo.serviceInfo);
            if (interactionServiceInfo.getParseError() != null) {
                LOG.w("Error in VoiceInteractionService " + resolveInfo.serviceInfo.packageName
                        + "/" + resolveInfo.serviceInfo.name + ": "
                        + interactionServiceInfo.getParseError());
                continue;
            }
            VoiceInteractionInfo voiceInteractionInfo = new VoiceInteractionInfo(mContext,
                    interactionServiceInfo);
            mVoiceInteractionInfoList.add(voiceInteractionInfo);
            if (interactionServiceInfo.getRecognitionService() != null) {
                mRecognitionServiceNames.add(new ComponentName(resolveInfo.serviceInfo.packageName,
                        interactionServiceInfo.getRecognitionService()));
            }
            mComponentToInfoMap.put(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name), voiceInteractionInfo);
        }
        Collections.sort(mVoiceInteractionInfoList);
    }

    private void loadVoiceRecognitionServices() {
        List<ResolveInfo> mAvailableRecognitionServices =
                mContext.getPackageManager().queryIntentServices(VOICE_RECOGNITION_SERVICE_TAG,
                        PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : mAvailableRecognitionServices) {
            ComponentName componentName = new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name);

            VoiceRecognitionInfo voiceRecognitionInfo = new VoiceRecognitionInfo(mContext,
                    resolveInfo.serviceInfo);
            mVoiceRecognitionInfoList.add(voiceRecognitionInfo);
            mRecognitionServiceNames.add(componentName);
            mComponentToInfoMap.put(componentName, voiceRecognitionInfo);
        }
        Collections.sort(mVoiceRecognitionInfoList);
    }

    /**
     * Base object used to represent {@link VoiceInteractionInfo} and {@link VoiceRecognitionInfo}.
     */
    abstract static class VoiceInputInfo implements Comparable {
        private final Context mContext;
        private final ServiceInfo mServiceInfo;

        VoiceInputInfo(Context context, ServiceInfo serviceInfo) {
            mContext = context;
            mServiceInfo = serviceInfo;
        }

        protected Context getContext() {
            return mContext;
        }

        protected ServiceInfo getServiceInfo() {
            return mServiceInfo;
        }

        @Override
        public int compareTo(Object o) {
            return getTag().toString().compareTo(((VoiceInputInfo) o).getTag().toString());
        }

        /**
         * Returns the {@link ComponentName} which represents the settings activity, if it exists.
         */
        @Nullable
        ComponentName getSettingsActivityComponentName() {
            String activity = getSettingsActivity();
            return (activity != null) ? new ComponentName(mServiceInfo.packageName, activity)
                    : null;
        }

        /** Returns the package name for the service represented by this {@link VoiceInputInfo}. */
        String getPackageName() {
            return mServiceInfo.packageName;
        }

        /**
         * Returns the component name for the service represented by this {@link VoiceInputInfo}.
         */
        ComponentName getComponentName() {
            return new ComponentName(mServiceInfo.packageName, mServiceInfo.name);
        }

        /**
         * Returns the label to describe the service represented by this {@link VoiceInputInfo}.
         */
        abstract CharSequence getLabel();

        /**
         * The string representation of the settings activity for the service represented by this
         * {@link VoiceInputInfo}.
         */
        protected abstract String getSettingsActivity();

        /**
         * Returns a tag used to determine the sort order of the {@link VoiceInputInfo} instances.
         */
        protected CharSequence getTag() {
            return mServiceInfo.loadLabel(mContext.getPackageManager());
        }
    }

    /** An object to represent {@link VoiceInteractionService} instances. */
    static class VoiceInteractionInfo extends VoiceInputInfo {
        private final VoiceInteractionServiceInfo mInteractionServiceInfo;

        VoiceInteractionInfo(Context context, VoiceInteractionServiceInfo info) {
            super(context, info.getServiceInfo());

            mInteractionServiceInfo = info;
        }

        /** Returns the recognition service associated with this {@link VoiceInteractionService}. */
        String getRecognitionService() {
            return mInteractionServiceInfo.getRecognitionService();
        }

        @Override
        protected String getSettingsActivity() {
            return mInteractionServiceInfo.getSettingsActivity();
        }

        @Override
        CharSequence getLabel() {
            return getServiceInfo().applicationInfo.loadLabel(getContext().getPackageManager());
        }
    }

    /** An object to represent {@link RecognitionService} instances. */
    static class VoiceRecognitionInfo extends VoiceInputInfo {

        VoiceRecognitionInfo(Context context, ServiceInfo serviceInfo) {
            super(context, serviceInfo);
        }

        @Override
        protected String getSettingsActivity() {
            return getServiceSettingsActivity(getServiceInfo());
        }

        @Override
        CharSequence getLabel() {
            return getTag();
        }

        private String getServiceSettingsActivity(ServiceInfo serviceInfo) {
            XmlResourceParser parser = null;
            String settingActivity = null;
            try {
                parser = serviceInfo.loadXmlMetaData(getContext().getPackageManager(),
                        RecognitionService.SERVICE_META_DATA);
                if (parser == null) {
                    throw new XmlPullParserException(
                            "No " + RecognitionService.SERVICE_META_DATA + " meta-data for "
                                    + serviceInfo.packageName);
                }

                Resources res = getContext().getPackageManager().getResourcesForApplication(
                        serviceInfo.applicationInfo);

                AttributeSet attrs = Xml.asAttributeSet(parser);

                int type;
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && type != XmlPullParser.START_TAG) {
                    continue;
                }

                String nodeName = parser.getName();
                if (!"recognition-service".equals(nodeName)) {
                    throw new XmlPullParserException(
                            "Meta-data does not start with recognition-service tag");
                }

                TypedArray array = res.obtainAttributes(attrs,
                        com.android.internal.R.styleable.RecognitionService);
                settingActivity = array.getString(
                        com.android.internal.R.styleable.RecognitionService_settingsActivity);
                array.recycle();
            } catch (XmlPullParserException e) {
                LOG.e("error parsing recognition service meta-data", e);
            } catch (IOException e) {
                LOG.e("error parsing recognition service meta-data", e);
            } catch (PackageManager.NameNotFoundException e) {
                LOG.e("error parsing recognition service meta-data", e);
            } finally {
                if (parser != null) parser.close();
            }

            return settingActivity;
        }
    }
}
