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
 * limitations under the License.
 */

package com.android.documentsui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.IntDef;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.ui.MessageBuilder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Alert dialog for operation dialogs.
 */
public class OperationDialogFragment extends DialogFragment {

    public static final int DIALOG_TYPE_UNKNOWN = 0;
    public static final int DIALOG_TYPE_FAILURE = 1;
    public static final int DIALOG_TYPE_CONVERTED = 2;

    @IntDef(flag = true, value = {
        DIALOG_TYPE_UNKNOWN,
        DIALOG_TYPE_FAILURE,
        DIALOG_TYPE_CONVERTED
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface DialogType {}

    private static final String TAG = "OperationDialogFragment";

    public static void show(
            FragmentManager fm,
            @DialogType int dialogType,
            ArrayList<DocumentInfo> failedSrcList,
            ArrayList<Uri> uriList,
            DocumentStack dstStack,
            @OpType int operationType) {

        final Bundle args = new Bundle();
        args.putInt(FileOperationService.EXTRA_DIALOG_TYPE, dialogType);
        args.putInt(FileOperationService.EXTRA_OPERATION_TYPE, operationType);
        args.putParcelableArrayList(FileOperationService.EXTRA_FAILED_DOCS, failedSrcList);
        args.putParcelableArrayList(FileOperationService.EXTRA_FAILED_URIS, uriList);

        final FragmentTransaction ft = fm.beginTransaction();
        final OperationDialogFragment fragment = new OperationDialogFragment();
        fragment.setArguments(args);

        ft.add(fragment, TAG);
        ft.commitAllowingStateLoss();
    }

    @Override
    public Dialog onCreateDialog(Bundle inState) {
        super.onCreate(inState);

        final @DialogType int dialogType =
              getArguments().getInt(FileOperationService.EXTRA_DIALOG_TYPE);
        final @OpType int operationType =
              getArguments().getInt(FileOperationService.EXTRA_OPERATION_TYPE);
        final ArrayList<Uri> uriList = getArguments().getParcelableArrayList(
                FileOperationService.EXTRA_FAILED_URIS);
        final ArrayList<DocumentInfo> docList = getArguments().getParcelableArrayList(
                FileOperationService.EXTRA_FAILED_DOCS);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        final String message = new MessageBuilder(getContext()).generateListMessage(
                dialogType, operationType, docList, uriList);

        builder.setMessage(Html.fromHtml(message));
        builder.setPositiveButton(
                R.string.close,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        return builder.create();
    }
}
