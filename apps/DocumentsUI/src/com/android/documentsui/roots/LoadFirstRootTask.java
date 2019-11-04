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

package com.android.documentsui.roots;

import android.app.Activity;
import android.net.Uri;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

import java.util.Collection;

public final class LoadFirstRootTask<T extends Activity & CommonAddons>
        extends LoadRootTask<T> {

    public LoadFirstRootTask(T activity, ProvidersAccess providers, State state, Uri rootUri) {
        super(activity, providers, state, rootUri);
    }

    @Override
    protected String getRootId(Uri rootUri) {
        final String authority = rootUri.getAuthority();
        String rootId = null;

        final Collection<RootInfo> roots = mProviders.getRootsForAuthorityBlocking(authority);
        if (!roots.isEmpty()) {
            rootId = roots.iterator().next().rootId;
        }

        return rootId;
    }
}
