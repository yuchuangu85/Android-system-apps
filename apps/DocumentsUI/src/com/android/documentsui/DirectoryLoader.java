/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.VERBOSE;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.RemoteException;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import androidx.loader.content.AsyncTaskLoader;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.FilteringCursorWrapper;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.sorting.SortModel;

import java.util.concurrent.Executor;

public class DirectoryLoader extends AsyncTaskLoader<DirectoryResult> {

    private static final String TAG = "DirectoryLoader";

    private static final String[] SEARCH_REJECT_MIMES = new String[] { Document.MIME_TYPE_DIR };
    private static final String[] PHOTO_PICKING_ACCEPT_MIMES = new String[]
            {Document.MIME_TYPE_DIR, MimeTypes.IMAGE_MIME};

    private final LockingContentObserver mObserver;
    private final RootInfo mRoot;
    private final Uri mUri;
    private final SortModel mModel;
    private final Lookup<String, String> mFileTypeLookup;
    private final boolean mSearchMode;
    private final Bundle mQueryArgs;
    private final boolean mPhotoPicking;

    private DocumentInfo mDoc;
    private CancellationSignal mSignal;
    private DirectoryResult mResult;

    private Features mFeatures;

    public DirectoryLoader(
            Features features,
            Context context,
            State state,
            Uri uri,
            Lookup<String, String> fileTypeLookup,
            ContentLock lock,
            Bundle queryArgs) {

        super(context);
        mFeatures = features;
        mRoot = state.stack.getRoot();
        mUri = uri;
        mModel = state.sortModel;
        mDoc = state.stack.peek();
        mFileTypeLookup = fileTypeLookup;
        mSearchMode = queryArgs != null;
        mQueryArgs = queryArgs;
        mObserver = new LockingContentObserver(lock, this::onContentChanged);
        mPhotoPicking = state.isPhotoPicking();
    }

    @Override
    protected Executor getExecutor() {
        return ProviderExecutor.forAuthority(mRoot.authority);
    }

    @Override
    public final DirectoryResult loadInBackground() {
        synchronized (this) {
            if (isLoadInBackgroundCanceled()) {
                throw new OperationCanceledException();
            }
            mSignal = new CancellationSignal();
        }

        final ContentResolver resolver = getContext().getContentResolver();
        final String authority = mUri.getAuthority();

        final DirectoryResult result = new DirectoryResult();
        result.doc = mDoc;

        ContentProviderClient client = null;
        Cursor cursor;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority);
            if (mDoc.isInArchive()) {
                ArchivesProvider.acquireArchive(client, mUri);
            }
            result.client = client;

            final Bundle queryArgs = new Bundle();
            mModel.addQuerySortArgs(queryArgs);

            if (mSearchMode) {
                queryArgs.putAll(mQueryArgs);
            }

            if (mFeatures.isContentPagingEnabled()) {
                // TODO: At some point we don't want forced flags to override real paging...
                // and that point is when we have real paging.
                DebugFlags.addForcedPagingArgs(queryArgs);
            }

            cursor = client.query(mUri, null, queryArgs, mSignal);

            if (cursor == null) {
                throw new RemoteException("Provider returned null");
            }

            cursor.registerContentObserver(mObserver);

            cursor = new RootCursorWrapper(mUri.getAuthority(), mRoot.rootId, cursor, -1);

            if (mSearchMode && !mFeatures.isFoldersInSearchResultsEnabled()) {
                // There is no findDocumentPath API. Enable filtering on folders in search mode.
                cursor = new FilteringCursorWrapper(cursor, null, SEARCH_REJECT_MIMES);
            }

            if (mPhotoPicking) {
                cursor = new FilteringCursorWrapper(cursor, PHOTO_PICKING_ACCEPT_MIMES, null);
            }

            // TODO: When API tweaks have landed, use ContentResolver.EXTRA_HONORED_ARGS
            // instead of checking directly for ContentResolver.QUERY_ARG_SORT_COLUMNS (won't work)
            if (mFeatures.isContentPagingEnabled()
                    && cursor.getExtras().containsKey(ContentResolver.QUERY_ARG_SORT_COLUMNS)) {
                if (VERBOSE) Log.d(TAG, "Skipping sort of pre-sorted cursor. Booya!");
            } else {
                cursor = mModel.sortCursor(cursor, mFileTypeLookup);
            }
            result.cursor = cursor;
        } catch (Exception e) {
            Log.w(TAG, "Failed to query", e);
            result.exception = e;
        } finally {
            synchronized (this) {
                mSignal = null;
            }
            // TODO: Remove this call.
            FileUtils.closeQuietly(client);
        }

        return result;
    }

    @Override
    public void cancelLoadInBackground() {
        super.cancelLoadInBackground();

        synchronized (this) {
            if (mSignal != null) {
                mSignal.cancel();
            }
        }
    }

    @Override
    public void deliverResult(DirectoryResult result) {
        if (isReset()) {
            FileUtils.closeQuietly(result);
            return;
        }
        DirectoryResult oldResult = mResult;
        mResult = result;

        if (isStarted()) {
            super.deliverResult(result);
        }

        if (oldResult != null && oldResult != result) {
            FileUtils.closeQuietly(oldResult);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResult != null) {
            deliverResult(mResult);
        }
        if (takeContentChanged() || mResult == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(DirectoryResult result) {
        FileUtils.closeQuietly(result);
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        FileUtils.closeQuietly(mResult);
        mResult = null;

        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    private static final class LockingContentObserver extends ContentObserver {
        private final ContentLock mLock;
        private final Runnable mContentChangedCallback;

        public LockingContentObserver(ContentLock lock, Runnable contentChangedCallback) {
            super(new Handler(Looper.getMainLooper()));
            mLock = lock;
            mContentChangedCallback = contentChangedCallback;
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) {
                Log.d(TAG, "Directory content updated.");
            }
            mLock.runWhenUnlocked(mContentChangedCallback);
        }
    }
}
