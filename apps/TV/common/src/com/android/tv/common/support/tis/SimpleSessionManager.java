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

import android.media.tv.TvInputService.Session;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

/** A simple session manager that allows a maximum number of concurrent session. */
public final class SimpleSessionManager implements SessionManager {

    private final Set<Session> sessions;
    private final int max;

    public SimpleSessionManager(int max) {
        this.max = max;
        sessions = VERSION.SDK_INT >= VERSION_CODES.M ? new ArraySet<>() : new HashSet<>();
    }

    @Override
    public void removeSession(Session session) {
        sessions.remove(session);
    }

    @Override
    public void addSession(Session session) {
        sessions.add(session);
    }

    @Override
    public boolean canCreateNewSession() {
        return sessions.size() < max;
    }

    @Override
    public ImmutableSet<Session> getSessions() {
        return ImmutableSet.copyOf(sessions);
    }

    @VisibleForTesting
    int getSessionCount() {
        return sessions.size();
    }
}
