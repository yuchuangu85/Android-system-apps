/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.picker;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Used to confirm with user that it's OK to overwrite an existing file.
 */
public class ConfirmFragment extends DialogFragment {

    private static final String TAG = "ConfirmFragment";

    public static final String CONFIRM_TYPE = "type";
    public static final int TYPE_OVERWRITE = 1;
    public static final int TYPE_OEPN_TREE = 2;

    private ActionHandler<PickActivity> mActions;
    private DocumentInfo mTarget;
    private int mType;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActions = ((PickActivity) getActivity()).getInjector().actions;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = (getArguments() != null) ? getArguments() : savedInstanceState;

        mTarget = arg.getParcelable(Shared.EXTRA_DOC);
        mType = arg.getInt(CONFIRM_TYPE);
        final PickResult pickResult = ((PickActivity) getActivity()).getInjector().pickResult;

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        switch (mType) {
            case TYPE_OVERWRITE:
                String message = String.format(
                        getString(R.string.overwrite_file_confirmation_message),
                        mTarget.displayName);
                builder.setMessage(message);
                builder.setPositiveButton(
                        android.R.string.ok,
                        (DialogInterface dialog, int id) -> {
                            pickResult.increaseActionCount();
                            mActions.finishPicking(mTarget.derivedUri);
                        });
                break;
            case TYPE_OEPN_TREE:
                final Uri uri = DocumentsContract.buildTreeDocumentUri(
                        mTarget.authority, mTarget.documentId);
                final BaseActivity activity = (BaseActivity) getActivity();
                final String target = activity.getCurrentTitle();
                final String location = activity.getCurrentRoot().title;
                final String text = getString(R.string.open_tree_dialog_title, target, location);
                message = getString(R.string.open_tree_dialog_message,
                        getAppName(getActivity().getCallingPackage()));

                builder.setTitle(text);
                builder.setMessage(message);
                builder.setPositiveButton(
                        R.string.allow,
                        (DialogInterface dialog, int id) -> {
                            pickResult.increaseActionCount();
                            mActions.finishPicking(uri);
                        });
                break;

        }
        builder.setNegativeButton(android.R.string.cancel,
                (DialogInterface dialog, int id) -> pickResult.increaseActionCount());

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(Shared.EXTRA_DOC, mTarget);
        outState.putInt(CONFIRM_TYPE, mType);
    }

    private String getAppName(String packageName) {
        final String anonymous = getString(R.string.anonymous_application);
        if (TextUtils.isEmpty(packageName)) {
            return anonymous;
        }

        final PackageManager pm = getContext().getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return anonymous;
        }

        CharSequence result = pm.getApplicationLabel(ai);
        return TextUtils.isEmpty(result) ? anonymous : result.toString();
    }

    public static void show(FragmentManager fm, DocumentInfo overwriteTarget, int type) {
        Bundle arg = new Bundle();
        arg.putParcelable(Shared.EXTRA_DOC, overwriteTarget);
        arg.putInt(CONFIRM_TYPE, type);

        FragmentTransaction ft = fm.beginTransaction();
        Fragment f = new ConfirmFragment();
        f.setArguments(arg);
        ft.add(f, TAG);
        ft.commitAllowingStateLoss();
    }
}
