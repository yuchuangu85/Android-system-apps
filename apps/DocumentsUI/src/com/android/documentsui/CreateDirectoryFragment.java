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

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.base.SharedMinimal.TAG;

import android.app.Dialog;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.ui.Snackbars;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Dialog to create a new directory.
 */
public class CreateDirectoryFragment extends DialogFragment {
    private static final String TAG_CREATE_DIRECTORY = "create_directory";

    public static void show(FragmentManager fm) {
        final CreateDirectoryFragment dialog = new CreateDirectoryFragment();
        dialog.show(fm, TAG_CREATE_DIRECTORY);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity();
        final ContentResolver resolver = context.getContentResolver();

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        final LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());

        final View view = dialogInflater.inflate(R.layout.dialog_file_name, null, false);
        final EditText editText = (EditText) view.findViewById(android.R.id.text1);

        final TextInputLayout inputWrapper = view.findViewById(R.id.input_wrapper);
        inputWrapper.setHint(getString(R.string.input_hint_new_folder));

        builder.setTitle(R.string.menu_create_dir);
        builder.setView(view);

        builder.setPositiveButton(
                android.R.string.ok,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createDirectory(editText.getText().toString());
                    }
                });

        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.create();

        // Workaround for the problem - virtual keyboard doesn't show on the phone.
        Shared.ensureKeyboardPresent(context, dialog);

        editText.setOnEditorActionListener(
                new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(
                            TextView view, int actionId, @Nullable KeyEvent event) {
                        if ((actionId == EditorInfo.IME_ACTION_DONE) || (event != null
                                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                && event.hasNoModifiers())) {
                            createDirectory(editText.getText().toString());
                            dialog.dismiss();
                            return true;
                        }
                        return false;
                    }
                });
        editText.requestFocus();

        return dialog;
    }

    private void createDirectory(String name) {
        final BaseActivity activity = (BaseActivity) getActivity();
        final DocumentInfo cwd = activity.getCurrentDirectory();

        new CreateDirectoryTask(activity, cwd, name).executeOnExecutor(
                ProviderExecutor.forAuthority(cwd.authority));
    }

    private class CreateDirectoryTask extends AsyncTask<Void, Void, DocumentInfo> {
        private final BaseActivity mActivity;
        private final DocumentInfo mCwd;
        private final String mDisplayName;

        public CreateDirectoryTask(
                BaseActivity activity, DocumentInfo cwd, String displayName) {
            mActivity = activity;
            mCwd = cwd;
            mDisplayName = displayName;
        }

        @Override
        protected void onPreExecute() {
            mActivity.setPending(true);
        }

        @Override
        protected DocumentInfo doInBackground(Void... params) {
            final ContentResolver resolver = mActivity.getContentResolver();
            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, mCwd.derivedUri.getAuthority());
                final Uri childUri = DocumentsContract.createDocument(
                        wrap(client), mCwd.derivedUri, Document.MIME_TYPE_DIR, mDisplayName);
                DocumentInfo doc = DocumentInfo.fromUri(resolver, childUri);
                return doc.isDirectory() ? doc : null;
            } catch (Exception e) {
                Log.w(TAG, "Failed to create directory", e);
                return null;
            } finally {
                FileUtils.closeQuietly(client);
            }
        }

        @Override
        protected void onPostExecute(DocumentInfo result) {
            if (result != null) {
                // Navigate into newly created child
                mActivity.onDirectoryCreated(result);
                Metrics.logCreateDirOperation();
            } else {
                Snackbars.makeSnackbar(mActivity, R.string.create_error, Snackbar.LENGTH_SHORT)
                        .show();
                Metrics.logCreateDirError();
            }
            mActivity.setPending(false);
        }
    }
}
