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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.TAG;

import android.app.ActivityManager;
import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.loader.content.AsyncTaskLoader;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.FilteringCursorWrapper;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.roots.RootCursorWrapper;

import com.google.common.util.concurrent.AbstractFuture;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * The abstract class to query multiple roots from {@link android.provider.DocumentsProvider}
 * and return the combined result.
 */
public abstract class MultiRootDocumentsLoader extends AsyncTaskLoader<DirectoryResult> {
    // TODO: clean up cursor ownership so background thread doesn't traverse
    // previously returned cursors for filtering/sorting; this currently races
    // with the UI thread.

    private static final int MAX_OUTSTANDING_TASK = 4;
    private static final int MAX_OUTSTANDING_TASK_SVELTE = 2;

    /**
     * Time to wait for first pass to complete before returning partial results.
     */
    private static final int MAX_FIRST_PASS_WAIT_MILLIS = 500;

    protected final State mState;

    private final Semaphore mQueryPermits;
    private final ProvidersAccess mProviders;
    private final Lookup<String, Executor> mExecutors;
    private final Lookup<String, String> mFileTypeMap;

    @GuardedBy("mTasks")
    /** A authority -> QueryTask map */
    private final Map<String, QueryTask> mTasks = new HashMap<>();

    private CountDownLatch mFirstPassLatch;
    private volatile boolean mFirstPassDone;

    private DirectoryResult mResult;

    /*
     * Create the loader to query roots from {@link android.provider.DocumentsProvider}.
     *
     * @param context the context
     * @param providers the providers
     * @param state current state
     * @param executors the executors of authorities
     * @param fileTypeMap the map of mime types and file types.
     */
    public MultiRootDocumentsLoader(Context context, ProvidersAccess providers, State state,
            Lookup<String, Executor> executors, Lookup<String, String> fileTypeMap) {

        super(context);
        mProviders = providers;
        mState = state;
        mExecutors = executors;
        mFileTypeMap = fileTypeMap;

        // Keep clients around on high-RAM devices, since we'd be spinning them
        // up moments later to fetch thumbnails anyway.
        final ActivityManager am = (ActivityManager) getContext().getSystemService(
                Context.ACTIVITY_SERVICE);
        mQueryPermits = new Semaphore(
                am.isLowRamDevice() ? MAX_OUTSTANDING_TASK_SVELTE : MAX_OUTSTANDING_TASK);
    }

    @Override
    public DirectoryResult loadInBackground() {
        synchronized (mTasks) {
            return loadInBackgroundLocked();
        }
    }

    private DirectoryResult loadInBackgroundLocked() {
        if (mFirstPassLatch == null) {
            // First time through we kick off all the recent tasks, and wait
            // around to see if everyone finishes quickly.
            Map<String, List<RootInfo>> rootsIndex = indexRoots();

            for (Map.Entry<String, List<RootInfo>> rootEntry : rootsIndex.entrySet()) {
                mTasks.put(rootEntry.getKey(),
                        getQueryTask(rootEntry.getKey(), rootEntry.getValue()));
            }

            mFirstPassLatch = new CountDownLatch(mTasks.size());
            for (QueryTask task : mTasks.values()) {
                mExecutors.lookup(task.authority).execute(task);
            }

            try {
                mFirstPassLatch.await(MAX_FIRST_PASS_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                mFirstPassDone = true;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        final long rejectBefore = getRejectBeforeTime();

        // Collect all finished tasks
        boolean allDone = true;
        int totalQuerySize = 0;
        List<Cursor> cursors = new ArrayList<>(mTasks.size());
        for (QueryTask task : mTasks.values()) {
            if (task.isDone()) {
                try {
                    final Cursor[] taskCursors = task.get();
                    if (taskCursors == null || taskCursors.length == 0) {
                        continue;
                    }

                    totalQuerySize += taskCursors.length;
                    for (Cursor cursor : taskCursors) {
                        if (cursor == null) {
                            // It's possible given an authority, some roots fail to return a cursor
                            // after a query.
                            continue;
                        }
                        final FilteringCursorWrapper filtered = new FilteringCursorWrapper(
                                cursor, mState.acceptMimes, getRejectMimes(), rejectBefore) {
                            @Override
                            public void close() {
                                // Ignored, since we manage cursor lifecycle internally
                            }
                        };
                        cursors.add(filtered);
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    // We already logged on other side
                } catch (Exception e) {
                    // Catch exceptions thrown when we read the cursor.
                    Log.e(TAG, "Failed to query documents for authority: " + task.authority
                            + ". Skip this authority.", e);
                }
            } else {
                allDone = false;
            }
        }

        if (DEBUG) {
            Log.d(TAG,
                    "Found " + cursors.size() + " of " + totalQuerySize + " queries done");
        }

        final DirectoryResult result = new DirectoryResult();
        result.doc = new DocumentInfo();

        final Cursor merged;
        if (cursors.size() > 0) {
            merged = new MergeCursor(cursors.toArray(new Cursor[cursors.size()]));
        } else {
            // Return something when nobody is ready
            merged = new MatrixCursor(new String[0]);
        }

        final Cursor sorted;
        if (isDocumentsMovable()) {
            sorted = mState.sortModel.sortCursor(merged, mFileTypeMap);
        } else {
            final Cursor notMovableMasked = new NotMovableMaskCursor(merged);
            sorted = mState.sortModel.sortCursor(notMovableMasked, mFileTypeMap);
        }

        // Tell the UI if this is an in-progress result. When loading is complete, another update is
        // sent with EXTRA_LOADING set to false.
        Bundle extras = new Bundle();
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, !allDone);
        sorted.setExtras(extras);

        result.cursor = sorted;

        return result;
    }

    /**
     * Returns a map of Authority -> rootInfos.
     */
    private Map<String, List<RootInfo>> indexRoots() {
        final Collection<RootInfo> roots = mProviders.getMatchingRootsBlocking(mState);
        HashMap<String, List<RootInfo>> rootsIndex = new HashMap<>();
        for (RootInfo root : roots) {
            // ignore the root with authority is null. e.g. Recent
            if (root.authority == null || shouldIgnoreRoot(root)) {
                continue;
            }

            if (!rootsIndex.containsKey(root.authority)) {
                rootsIndex.put(root.authority, new ArrayList<>());
            }
            rootsIndex.get(root.authority).add(root);
        }

        return rootsIndex;
    }

    protected long getRejectBeforeTime() {
        return -1;
    }

    protected String[] getRejectMimes() {
        return null;
    }

    protected boolean shouldIgnoreRoot(RootInfo root) {
        return false;
    }

    protected boolean isDocumentsMovable() {
        return false;
    }

    protected abstract QueryTask getQueryTask(String authority, List<RootInfo> rootInfos);

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

        synchronized (mTasks) {
            for (QueryTask task : mTasks.values()) {
                FileUtils.closeQuietly(task);
            }
        }

        FileUtils.closeQuietly(mResult);
        mResult = null;
    }

    // TODO: create better transfer of ownership around cursor to ensure its
    // closed in all edge cases.

    private static class NotMovableMaskCursor extends CursorWrapper {
        private static final int NOT_MOVABLE_MASK =
                ~(Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_REMOVE
                        | Document.FLAG_SUPPORTS_MOVE);

        private NotMovableMaskCursor(Cursor cursor) {
            super(cursor);
        }

        @Override
        public int getInt(int index) {
            final int flagIndex = getWrappedCursor().getColumnIndex(Document.COLUMN_FLAGS);
            final int value = super.getInt(index);
            return (index == flagIndex) ? (value & NOT_MOVABLE_MASK) : value;
        }
    }

    protected abstract class QueryTask extends AbstractFuture<Cursor[]> implements Runnable,
            Closeable {
        public final String authority;
        public final List<RootInfo> rootInfos;

        private Cursor[] mCursors;
        private boolean mIsClosed = false;

        public QueryTask(String authority, List<RootInfo> rootInfos) {
            this.authority = authority;
            this.rootInfos = rootInfos;
        }

        @Override
        public void run() {
            if (isCancelled()) {
                return;
            }

            try {
                mQueryPermits.acquire();
            } catch (InterruptedException e) {
                return;
            }

            try {
                runInternal();
            } finally {
                mQueryPermits.release();
            }
        }

        protected abstract Uri getQueryUri(RootInfo rootInfo);

        protected abstract RootCursorWrapper generateResultCursor(RootInfo rootInfo,
                Cursor oriCursor);

        protected void addQueryArgs(@NonNull Bundle queryArgs) {
        }

        private synchronized void runInternal() {
            if (mIsClosed) {
                return;
            }

            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        getContext().getContentResolver(), authority);

                final int rootInfoCount = rootInfos.size();
                final Cursor[] res = new Cursor[rootInfoCount];
                mCursors = new Cursor[rootInfoCount];

                for (int i = 0; i < rootInfoCount; i++) {
                    final Uri uri = getQueryUri(rootInfos.get(i));
                    try {
                        final Bundle queryArgs = new Bundle();
                        mState.sortModel.addQuerySortArgs(queryArgs);
                        addQueryArgs(queryArgs);
                        res[i] = client.query(uri, null, queryArgs, null);
                        mCursors[i] = generateResultCursor(rootInfos.get(i), res[i]);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load " + authority + ", " + rootInfos.get(i).rootId,
                                e);
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire content resolver for authority: " + authority);
            } finally {
                FileUtils.closeQuietly(client);
            }

            set(mCursors);

            mFirstPassLatch.countDown();
            if (mFirstPassDone) {
                onContentChanged();
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (mCursors == null) {
                return;
            }

            for (Cursor cursor : mCursors) {
                FileUtils.closeQuietly(cursor);
            }

            mIsClosed = true;
        }
    }
}
