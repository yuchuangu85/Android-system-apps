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

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;

import java.util.List;

/**
 * A fragment which shows the rationale when requesting android.permission.WRITE_EXTERNAL_STORAGE.
 */
public class DvrWriteStoragePermissionRationaleFragment extends DvrGuidedStepFragment {
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Resources res = getContext().getResources();
        String title = res.getString(R.string.write_storage_permission_rationale_title);
        String description = res.getString(R.string.write_storage_permission_rationale_description);
        return new GuidanceStylist.Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(
                new GuidedAction.Builder(activity)
                        .id(GuidedAction.ACTION_ID_OK)
                        .title(android.R.string.ok)
                        .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        dismissDialog();
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrWriteStoragePermissionRationaleFragment";
    }
}
