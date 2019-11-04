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

package com.android.tv.settings.overlay;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsFragmentProvider;

/**
 * Abstract class for creating feature controllers. Allows customization of the settings app. To
 * provide a factory implementation, implementers should override
 * {@link R.string#config_featureFactory} in their override.
 */
@Keep
public abstract class FeatureFactory {

    protected static final String TAG = "FeatureFactory";
    private static final boolean DEBUG = false;

    protected static final String EXTRA_FRAGMENT_CLASS_NAME = "fragmentClassName";

    protected static FeatureFactory sFactory;

    /**
     * Returns a factory for creating feature controllers. Creates the factory if it does not
     * already exist. Uses the value of {@link R.string#config_featureFactory} to instantiate
     * a factory implementation.
     */
    public static FeatureFactory getFactory(Context context) {
        if (sFactory != null) {
            return sFactory;
        }

        if (DEBUG) {
            Log.d(TAG, "getFactory");
        }
        final String clsName = context.getString(R.string.config_featureFactory);
        if (TextUtils.isEmpty(clsName)) {
            throw new UnsupportedOperationException("No feature factory configured");
        }
        try {
            sFactory = (FeatureFactory) context.getClassLoader().loadClass(clsName).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new FactoryNotFoundException(e);
        }

        if (DEBUG) {
            Log.d(TAG, "started " + sFactory.getClass().getSimpleName());
        }
        return sFactory;
    }

    /** Supplies a provider that can create settings fragments. */
    public abstract SettingsFragmentProvider getSettingsFragmentProvider();

    /** Determines whether the layout shows two panels or one. */
    public abstract boolean isTwoPanelLayout();

    /** Exception thrown if the feature factory has not been defined. */
    public static final class FactoryNotFoundException extends RuntimeException {
        public FactoryNotFoundException(Throwable throwable) {
            super("Unable to create factory. Did you misconfigure Proguard?", throwable);
        }
    }
}
