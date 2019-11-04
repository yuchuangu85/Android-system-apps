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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.format.DateUtils;

import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.roots.RootCursorWrapper;

import java.util.List;
import java.util.concurrent.Executor;

public class RecentsLoader extends MultiRootDocumentsLoader {

    /** Ignore documents older than this age. */
    private static final long REJECT_OLDER_THAN = 45 * DateUtils.DAY_IN_MILLIS;

    /** MIME types that should always be excluded from recents. */
    private static final String[] REJECT_MIMES = new String[]{Document.MIME_TYPE_DIR};

    /** Maximum documents from a single root. */
    private static final int MAX_DOCS_FROM_ROOT = 64;

    public RecentsLoader(Context context, ProvidersAccess providers, State state,
            Lookup<String, Executor> executors, Lookup<String, String> fileTypeMap) {
        super(context, providers, state, executors, fileTypeMap);
    }

    @Override
    protected long getRejectBeforeTime() {
        return System.currentTimeMillis() - REJECT_OLDER_THAN;
    }

    @Override
    protected String[] getRejectMimes() {
        return REJECT_MIMES;
    }

    @Override
    protected boolean shouldIgnoreRoot(RootInfo root) {
        // only query the root is local only and support recents
        return !root.isLocalOnly() || !root.supportsRecents();
    }

    @Override
    protected QueryTask getQueryTask(String authority, List<RootInfo> rootInfos) {
        return new RecentsTask(authority, rootInfos);
    }

    private class RecentsTask extends QueryTask {

        public RecentsTask(String authority, List<RootInfo> rootInfos) {
            super(authority, rootInfos);
        }

        @Override
        protected Uri getQueryUri(RootInfo rootInfo) {
            return DocumentsContract.buildRecentDocumentsUri(authority, rootInfo.rootId);
        }

        @Override
        protected RootCursorWrapper generateResultCursor(RootInfo rootInfo, Cursor oriCursor) {
            return new RootCursorWrapper(authority, rootInfo.rootId, oriCursor, MAX_DOCS_FROM_ROOT);
        }
    }
}
