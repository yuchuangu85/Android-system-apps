/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifies clients when the app has changed the wallpaper.
 */
public class WallpaperChangedNotifier {

    private static final Object sInstanceLock = new Object();
    private static WallpaperChangedNotifier sInstance;
    private List<Listener> mListeners;

    /**
     * Make the constructor private to prevent instantiation outside the singleton getInstance()
     * method.
     */
    private WallpaperChangedNotifier() {
        mListeners = new ArrayList<>();
    }

    public static WallpaperChangedNotifier getInstance() {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new WallpaperChangedNotifier();
            }
            return sInstance;
        }
    }

    /**
     * Notifies all listeners that the wallpaper was changed.
     */
    public void notifyWallpaperChanged() {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onWallpaperChanged();
        }
    }

    /**
     * Registers a listener for wallpaper change events.
     */
    public void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Unregisters a listener for wallpaper change events.
     */
    public void unregisterListener(Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Listener for wallpaper changed notification.
     */
    public interface Listener {
        void onWallpaperChanged();
    }
}
