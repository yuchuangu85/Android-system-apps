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

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.app.AssistUtils;

import java.util.List;

/** Common logic for preference controllers that configure the assistant's behavior. */
public abstract class AssistConfigBasePreferenceController extends
        PreferenceController<TwoStatePreference> {

    private final SettingObserver mSettingObserver;
    private final AssistUtils mAssistUtils;
    private final CarUserManagerHelper mCarUserManagerHelper;

    public AssistConfigBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAssistUtils = new AssistUtils(context);
        mSettingObserver = new SettingObserver(getSettingUris(), this::refreshUi);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        int userId = mCarUserManagerHelper.getCurrentProcessUserId();
        return mAssistUtils.getAssistComponentForUser(userId) != null ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    protected void onStartInternal() {
        mSettingObserver.register(getContext().getContentResolver(), true);
    }

    @Override
    protected void onStopInternal() {
        mSettingObserver.register(getContext().getContentResolver(), false);
    }

    /** Gets the Setting Uris that should be observed */
    protected abstract List<Uri> getSettingUris();

    /**
     * Creates an observer that listens for changes to {@link Settings.Secure#ASSISTANT} as well as
     * any other URI defined by {@link #getSettingUris()}.
     */
    private static class SettingObserver extends ContentObserver {

        private static final Uri ASSIST_URI = Settings.Secure.getUriFor(Settings.Secure.ASSISTANT);
        private final List<Uri> mUriList;
        private final Runnable mSettingChangeListener;

        SettingObserver(List<Uri> uriList, Runnable settingChangeListener) {
            super(new Handler(Looper.getMainLooper()));
            mUriList = uriList;
            mSettingChangeListener = settingChangeListener;
        }

        /** Registers or unregisters this observer to the given content resolver. */
        void register(ContentResolver cr, boolean register) {
            if (register) {
                cr.registerContentObserver(ASSIST_URI, /* notifyForDescendants= */ false,
                        /* observer= */ this);
                if (mUriList != null) {
                    for (Uri uri : mUriList) {
                        cr.registerContentObserver(uri, /* notifyForDescendants= */ false,
                                /* observer=*/ this);
                    }
                }
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (shouldUpdatePreference(uri)) {
                mSettingChangeListener.run();
            }
        }

        private boolean shouldUpdatePreference(Uri uri) {
            return ASSIST_URI.equals(uri) || (mUriList != null && mUriList.contains(uri));
        }
    }
}
