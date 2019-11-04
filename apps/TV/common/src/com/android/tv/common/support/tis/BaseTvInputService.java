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
package com.android.tv.common.support.tis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.tv.common.support.tis.TifSession.TifSessionFactory;

/** Abstract TVInputService. */
public abstract class BaseTvInputService extends TvInputService {

    private static final IntentFilter INTENT_FILTER = new IntentFilter();

    static {
        INTENT_FILTER.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        INTENT_FILTER.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
    }

    @VisibleForTesting
    protected final BroadcastReceiver broadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (intent.getAction()) {
                        case TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED:
                        case TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED:
                            for (Session session : getSessionManager().getSessions()) {
                                if (session instanceof WrappedSession) {
                                    ((WrappedSession) session).onParentalControlsChanged();
                                }
                            }
                            break;
                        default:
                            // do nothing
                    }
                }
            };

    @Nullable
    @Override
    public final WrappedSession onCreateSession(String inputId) {
        SessionManager sessionManager = getSessionManager();
        if (sessionManager.canCreateNewSession()) {
            WrappedSession session =
                    new WrappedSession(
                            getApplicationContext(),
                            sessionManager,
                            getTifSessionFactory(),
                            inputId);
            sessionManager.addSession(session);
            return session;
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(broadcastReceiver, INTENT_FILTER);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    protected abstract TifSessionFactory getTifSessionFactory();

    protected abstract SessionManager getSessionManager();
}
