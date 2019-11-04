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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Implements(LocalBroadcastManager.class)
public class ShadowLocalBroadcastManager {
    private static List<Intent> sSentBroadcastIntents = new ArrayList<>();
    private static List<Wrapper> sRegisteredReceivers = new ArrayList<>();

    @Implementation
    public static LocalBroadcastManager getInstance(final Context context) {
        return ShadowApplication.getInstance().getSingleton(LocalBroadcastManager.class,
                () -> ReflectionHelpers.callConstructor(LocalBroadcastManager.class,
                        ClassParameter.from(Context.class, context)));
    }

    @Implementation
    public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        sRegisteredReceivers.add(new Wrapper(receiver, filter));
    }

    @Implementation
    public void unregisterReceiver(BroadcastReceiver receiver) {
        Iterator<Wrapper> iterator = sRegisteredReceivers.iterator();
        while (iterator.hasNext()) {
            Wrapper wrapper = iterator.next();
            if (wrapper.getBroadcastReceiver() == receiver) {
                iterator.remove();
            }
        }
    }

    @Implementation
    public boolean sendBroadcast(Intent intent) {
        boolean sent = false;
        sSentBroadcastIntents.add(intent);
        List<Wrapper> copy = new ArrayList<>(sRegisteredReceivers);
        for (Wrapper wrapper : copy) {
            if (wrapper.getIntentFilter().matchAction(intent.getAction())) {
                int match = wrapper.getIntentFilter().matchData(intent.getType(),
                        intent.getScheme(), intent.getData());
                if (match != IntentFilter.NO_MATCH_DATA && match != IntentFilter.NO_MATCH_TYPE) {
                    sent = true;
                    final BroadcastReceiver receiver = wrapper.getBroadcastReceiver();
                    final Intent broadcastIntent = intent;
                    Robolectric.getForegroundThreadScheduler().post(
                            (Runnable) () -> receiver.onReceive(RuntimeEnvironment.application,
                                    broadcastIntent));
                }
            }
        }
        return sent;
    }

    @Resetter
    public static void reset() {
        sSentBroadcastIntents.clear();
        sRegisteredReceivers.clear();
    }

    public static List<Intent> getSentBroadcastIntents() {
        return sSentBroadcastIntents;
    }

    public static List<Wrapper> getRegisteredBroadcastReceivers() {
        return sRegisteredReceivers;
    }

    public static class Wrapper {
        private final BroadcastReceiver mBroadcastReceiver;
        private final IntentFilter mIntentFilter;

        public Wrapper(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter) {
            this.mBroadcastReceiver = broadcastReceiver;
            this.mIntentFilter = intentFilter;
        }

        public BroadcastReceiver getBroadcastReceiver() {
            return mBroadcastReceiver;
        }

        public IntentFilter getIntentFilter() {
            return mIntentFilter;
        }
    }
}
