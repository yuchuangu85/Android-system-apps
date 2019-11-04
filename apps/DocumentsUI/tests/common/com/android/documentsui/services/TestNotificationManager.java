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

package com.android.documentsui.services;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.util.SparseArray;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

class TestNotificationManager {

    private final SparseArray<HashMap<String, Notification>> mNotifications = new SparseArray<>();
    private final Answer<Void> mAnswer = this::invoke;

    void notify(String tag, int id, Notification notification) {
        if (mNotifications.get(id) == null) {
            mNotifications.put(id, new HashMap<>());
        }

        mNotifications.get(id).put(tag, notification);
    }

    void cancel(String tag, int id) {
        final HashMap<String, Notification> idMap = mNotifications.get(id);
        if (idMap != null && idMap.containsKey(tag)) {
            idMap.remove(tag);
        }
    }

    private Void invoke(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        switch (invocation.getMethod().getName()) {
            case "notify":
                if (args.length == 2) {
                    notify(null, (Integer) args[0], (Notification) args[1]);
                }
                if (args.length == 3) {
                    notify((String) args[0], (Integer) args[1], (Notification) args[2]);
                }
                break;
            case "cancel":
                if (args.length == 1) {
                    cancel(null, (Integer) args[0]);
                }
                if (args.length == 2) {
                    cancel((String) args[0], (Integer) args[1]);
                }
                break;
        }
        return null;
    }

    private boolean hasNotification(int id, String jobId) {
        if (mNotifications.get(id) == null) {
            return false;
        }
        Notification notification = mNotifications.get(id).get(jobId);
        return notification != null;
    }

    NotificationManager createNotificationManager() {
        return Mockito.mock(NotificationManager.class, mAnswer);
    }

    void assertNumberOfNotifications(int expect) {
        int count = 0;
        for (int i = 0; i < mNotifications.size(); ++i) {
            count += mNotifications.valueAt(i).size();
        }

        assertEquals(expect, count);
    }

    void assertHasNotification(int id, String jobid) {
        assertTrue(hasNotification(id, jobid));
    }

    void assertNoNotification(int id, String jobid) {
        assertFalse(hasNotification(id, jobid));
    }
}
