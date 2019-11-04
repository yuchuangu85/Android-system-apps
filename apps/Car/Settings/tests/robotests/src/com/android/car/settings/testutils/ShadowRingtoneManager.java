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

package com.android.car.settings.testutils;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.HashMap;
import java.util.Map;

@Implements(RingtoneManager.class)
public class ShadowRingtoneManager {
    private static Ringtone sRingtone;
    private static Map<Integer, Uri> sSetDefaultRingtoneCalled = new HashMap<>();

    public static void setRingtone(Ringtone ringtone) {
        sRingtone = ringtone;
    }

    @Implementation
    protected static Ringtone getRingtone(final Context context, Uri ringtoneUri) {
        return sRingtone;
    }

    @Implementation
    public static void setActualDefaultRingtoneUri(Context context, int type, Uri ringtoneUri) {
        sSetDefaultRingtoneCalled.put(type, ringtoneUri);
    }

    @Implementation
    public static Uri getActualDefaultRingtoneUri(Context context, int type) {
        return sSetDefaultRingtoneCalled.getOrDefault(type, null);
    }

    @Resetter
    public static void reset() {
        sRingtone = null;
        sSetDefaultRingtoneCalled.clear();
    }
}
