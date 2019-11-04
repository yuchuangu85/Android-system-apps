/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Log;
import android.telecom.Logging.Runnable;
import android.telecom.Logging.Session;
import android.text.TextUtils;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CallerInfoLookupHelper {
    public interface OnQueryCompleteListener {
        /**
         * Called when the query returns with the caller info
         * @param info
         * @return true if the value should be cached, false otherwise.
         */
        void onCallerInfoQueryComplete(Uri handle, @Nullable CallerInfo info);
        void onContactPhotoQueryComplete(Uri handle, CallerInfo info);
    }

    private static class CallerInfoQueryInfo {
        public CallerInfo callerInfo;
        public List<OnQueryCompleteListener> listeners;
        public boolean imageQueryPending = false;

        public CallerInfoQueryInfo() {
            listeners = new LinkedList<>();
        }
    }

    private final Map<Uri, CallerInfoQueryInfo> mQueryEntries = new HashMap<>();

    private final CallerInfoAsyncQueryFactory mCallerInfoAsyncQueryFactory;
    private final ContactsAsyncHelper mContactsAsyncHelper;
    private final Context mContext;
    private final TelecomSystem.SyncRoot mLock;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public CallerInfoLookupHelper(Context context,
            CallerInfoAsyncQueryFactory callerInfoAsyncQueryFactory,
            ContactsAsyncHelper contactsAsyncHelper,
            TelecomSystem.SyncRoot lock) {
        mCallerInfoAsyncQueryFactory = callerInfoAsyncQueryFactory;
        mContactsAsyncHelper = contactsAsyncHelper;
        mContext = context;
        mLock = lock;
    }

    /**
     * Generates a CompletableFuture which performs a contacts lookup asynchronously.  The future
     * returns a {@link Pair} containing the original handle which is being looked up and any
     * {@link CallerInfo} which was found.
     * @param handle
     * @return {@link CompletableFuture} to perform the contacts lookup.
     */
    public CompletableFuture<Pair<Uri, CallerInfo>> startLookup(final Uri handle) {
        // Create the returned future and
        final CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();

        final String number = handle.getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            // Nothing to do here, just finish.
            Log.d(CallerInfoLookupHelper.this, "onCallerInfoQueryComplete - no number; end early");
            callerInfoFuture.complete(new Pair<>(handle, null));
            return callerInfoFuture;
        }

        // Setup a query complete listener which will get the results of the contacts lookup.
        OnQueryCompleteListener listener = new OnQueryCompleteListener() {
            @Override
            public void onCallerInfoQueryComplete(Uri handle, CallerInfo info) {
                Log.d(CallerInfoLookupHelper.this, "onCallerInfoQueryComplete - found info for %s",
                        Log.piiHandle(handle));
                // Got something, so complete the future.
                callerInfoFuture.complete(new Pair<>(handle, info));
            }

            @Override
            public void onContactPhotoQueryComplete(Uri handle, CallerInfo info) {
                // No-op for now; not something this future cares about.
            }
        };

        // Start async lookup.
        startLookup(handle, listener);

        return callerInfoFuture;
    }

    public void startLookup(final Uri handle, OnQueryCompleteListener listener) {
        if (handle == null) {
            listener.onCallerInfoQueryComplete(handle, null);
            return;
        }

        final String number = handle.getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            listener.onCallerInfoQueryComplete(handle, null);
            return;
        }

        synchronized (mLock) {
            if (mQueryEntries.containsKey(handle)) {
                CallerInfoQueryInfo info = mQueryEntries.get(handle);
                if (info.callerInfo != null) {
                    Log.i(this, "Caller info already exists for handle %s; using cached value",
                            Log.piiHandle(handle));
                    listener.onCallerInfoQueryComplete(handle, info.callerInfo);
                    if (!info.imageQueryPending && (info.callerInfo.cachedPhoto != null ||
                            info.callerInfo.cachedPhotoIcon != null)) {
                        listener.onContactPhotoQueryComplete(handle, info.callerInfo);
                    } else if (info.imageQueryPending) {
                        Log.i(this, "There is a pending photo query for handle %s. " +
                                "Adding to listeners for this query.", Log.piiHandle(handle));
                        info.listeners.add(listener);
                    }
                } else {
                    Log.i(this, "There is a previously incomplete query for handle %s. Adding to " +
                            "listeners for this query.", Log.piiHandle(handle));
                    info.listeners.add(listener);
                }
                // Since we have a pending query for this handle already, don't re-query it.
                return;
            } else {
                CallerInfoQueryInfo info = new CallerInfoQueryInfo();
                info.listeners.add(listener);
                mQueryEntries.put(handle, info);
            }
        }

        mHandler.post(new Runnable("CILH.sL", null) {
            @Override
            public void loggedRun() {
                Session continuedSession = Log.createSubsession();
                try {
                    CallerInfoAsyncQuery query = mCallerInfoAsyncQueryFactory.startQuery(
                            0, mContext, number,
                            makeCallerInfoQueryListener(handle), continuedSession);
                    if (query == null) {
                        Log.w(this, "Lookup failed for %s.", Log.piiHandle(handle));
                        Log.cancelSubsession(continuedSession);
                    }
                } catch (Throwable t) {
                    Log.cancelSubsession(continuedSession);
                    throw t;
                }
            }
        }.prepare());
    }

    private CallerInfoAsyncQuery.OnQueryCompleteListener makeCallerInfoQueryListener(
            final Uri handle) {
        return (token, cookie, ci) -> {
            synchronized (mLock) {
                Log.continueSession((Session) cookie, "CILH.oQC");
                try {
                    if (mQueryEntries.containsKey(handle)) {
                        Log.i(CallerInfoLookupHelper.this, "CI query for handle %s has completed;" +
                                " notifying all listeners.", Log.piiHandle(handle));
                        CallerInfoQueryInfo info = mQueryEntries.get(handle);
                        for (OnQueryCompleteListener l : info.listeners) {
                            l.onCallerInfoQueryComplete(handle, ci);
                        }
                        if (ci.contactDisplayPhotoUri == null) {
                            Log.i(CallerInfoLookupHelper.this, "There is no photo for this " +
                                    "contact, skipping photo query");
                            mQueryEntries.remove(handle);
                        } else {
                            info.callerInfo = ci;
                            info.imageQueryPending = true;
                            startPhotoLookup(handle, ci.contactDisplayPhotoUri);
                        }
                    } else {
                        Log.i(CallerInfoLookupHelper.this, "CI query for handle %s has completed," +
                                " but there are no listeners left.", Log.piiHandle(handle));
                    }
                } finally {
                    Log.endSession();
                }
            }
        };
    }

    private void startPhotoLookup(final Uri handle, final Uri contactPhotoUri) {
        mHandler.post(new Runnable("CILH.sPL", null) {
            @Override
            public void loggedRun() {
                Session continuedSession = Log.createSubsession();
                try {
                    mContactsAsyncHelper.startObtainPhotoAsync(
                            0, mContext, contactPhotoUri,
                            makeContactPhotoListener(handle), continuedSession);
                } catch (Throwable t) {
                    Log.cancelSubsession(continuedSession);
                    throw t;
                }
            }
        }.prepare());
    }

    private ContactsAsyncHelper.OnImageLoadCompleteListener makeContactPhotoListener(
            final Uri handle) {
        return (token, photo, photoIcon, cookie) -> {
            synchronized (mLock) {
                Log.continueSession((Session) cookie, "CLIH.oILC");
                try {
                    if (mQueryEntries.containsKey(handle)) {
                        CallerInfoQueryInfo info = mQueryEntries.get(handle);
                        if (info.callerInfo == null) {
                            Log.w(CallerInfoLookupHelper.this, "Photo query finished, but the " +
                                    "CallerInfo object previously looked up was not cached.");
                            mQueryEntries.remove(handle);
                            return;
                        }
                        info.callerInfo.cachedPhoto = photo;
                        info.callerInfo.cachedPhotoIcon = photoIcon;
                        for (OnQueryCompleteListener l : info.listeners) {
                            l.onContactPhotoQueryComplete(handle, info.callerInfo);
                        }
                        mQueryEntries.remove(handle);
                    } else {
                        Log.i(CallerInfoLookupHelper.this, "Photo query for handle %s has" +
                                " completed, but there are no listeners left.",
                                Log.piiHandle(handle));
                    }
                } finally {
                    Log.endSession();
                }
            }
        };
    }

    @VisibleForTesting
    public Map<Uri, CallerInfoQueryInfo> getCallerInfoEntries() {
        return mQueryEntries;
    }

    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }
}
