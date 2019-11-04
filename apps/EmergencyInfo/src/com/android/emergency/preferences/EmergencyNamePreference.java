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
package com.android.emergency.preferences;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.annotation.VisibleForTesting;
import androidx.preference.DialogPreference;
import com.android.emergency.CircleFramedDrawable;
import com.android.emergency.R;
import com.android.internal.util.UserIcons;
import com.android.settingslib.CustomDialogPreference;

import java.io.File;

/**
 * Custom {@link DialogPreference} that allows us to editing the user name and photo.
 */
public class EmergencyNamePreference extends CustomDialogPreference {

    private static final String KEY_AWAITING_RESULT = "awaiting_result";
    private static final String KEY_SAVED_PHOTO = "pending_photo";

    private UserManager mUserManager = getContext().getSystemService(UserManager.class);
    private EditUserPhotoController mEditUserPhotoController;
    private Fragment mFragment;
    private Bitmap mSavedPhoto;
    private EditText mUserNameView;
    private ImageView mUserPhotoView;
    private boolean mWaitingForActivityResult = false;

    public EmergencyNamePreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setSummary(mUserManager.getUserName());
        setIcon(getCircularUserIcon());
        setDialogLayoutResource(R.layout.edit_user_info_dialog_content);
    }

    public EmergencyNamePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EmergencyNamePreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.dialogPreferenceStyle);
    }

    public EmergencyNamePreference(Context context) {
        this(context, null);
    }

    /**
     * Setup fragment for Dialog and EditUserPhotoController.
     */
    public void setFragment(Fragment fragment) {
        mFragment = fragment;
    }

    /**
     * Reload user name and photo form UserManager.
     */
    public void reloadFromUserManager() {
        setSummary(mUserManager.getUserName());
        setIcon(getCircularUserIcon());
    }

    /**
     * Restore user photo when EditUserPhotoController had pending photo.
     */
    public void onRestoreInstanceState(Bundle icicle) {
        String pendingPhoto = icicle.getString(KEY_SAVED_PHOTO);
        if (pendingPhoto != null) {
            mSavedPhoto = EditUserPhotoController.loadNewUserPhotoBitmap(new File(pendingPhoto));
        }
        mWaitingForActivityResult = icicle.getBoolean(KEY_AWAITING_RESULT, false);
    }

    /**
     * Save a temp user photo when layout need to recreating but Dialog is showing.
     */
    public void onSaveInstanceState(Bundle outState) {
        if (getDialog() != null && getDialog().isShowing()
                && mEditUserPhotoController != null) {
            // Bitmap cannot be stored into bundle because it may exceed parcel limit
            // Store it in a temporary file instead
            File file = mEditUserPhotoController.saveNewUserPhotoBitmap();
            if (file != null) {
                outState.putString(KEY_SAVED_PHOTO, file.getPath());
            }
        }
        if (mWaitingForActivityResult) {
            outState.putBoolean(KEY_AWAITING_RESULT, mWaitingForActivityResult);
        }
    }

    /**
     * Set mWaitingForActivityResult to true when EmergencyNamePreferenceDialogFragment
     * startActivityForResult, means we are waiting the activity result.
     */
    public void startingActivityForResult() {
        mWaitingForActivityResult = true;
    }

    /**
     * Reset mWaitingForActivityResult and send the result code to EditUserPhotoController when
     * EmergencyNamePreferenceDialogFragment onActivityResult.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mWaitingForActivityResult = false;

        if (getDialog() != null) {
            mEditUserPhotoController.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mUserNameView = view.findViewById(R.id.user_name);
        mUserNameView.setText(mUserManager.getUserName());
        mUserPhotoView = view.findViewById(R.id.user_photo);
        Drawable drawable;
        if (mSavedPhoto != null) {
            drawable = CircleFramedDrawable.getInstance(getContext(), mSavedPhoto);
        } else {
            drawable = getCircularUserIcon();
        }
        mUserPhotoView.setImageDrawable(drawable);

        mEditUserPhotoController = createEditUserPhotoController(mUserPhotoView,
                getCircularUserIcon());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);
        builder.setTitle(R.string.name)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, listener)
                .create();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            // Update the name if changed.
            CharSequence userName = mUserNameView.getText();
            if (!TextUtils.isEmpty(userName)) {
                if (mUserManager.getUserName() == null
                        || !userName.toString().equals(mUserManager.getUserName())) {
                    mUserManager.setUserName(UserHandle.myUserId(), userName.toString());
                    setSummary(userName);
                }
            }
            // Update the photo if changed.
            Drawable drawable = mEditUserPhotoController.getNewUserPhotoDrawable();
            Bitmap bitmap = mEditUserPhotoController.getNewUserPhotoBitmap();
            if (drawable != null && bitmap != null
                    && !drawable.equals(getCircularUserIcon())) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        mUserManager.setUserIcon(UserHandle.myUserId(),
                                mEditUserPhotoController.getNewUserPhotoBitmap());
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                setIcon(drawable);
            }
            if (mFragment != null) {
                mFragment.getActivity().removeDialog(1);
            }
        }
        clear();
    }

    private void clear() {
        mEditUserPhotoController.removeNewUserPhotoBitmapFile();
        mSavedPhoto = null;
    }

    private Drawable getCircularUserIcon() {
        Bitmap bitmapUserIcon = mUserManager.getUserIcon(UserHandle.myUserId());

        if (bitmapUserIcon == null) {
            // get default user icon.
            final Drawable defaultUserIcon = UserIcons.getDefaultUserIcon(
                    getContext().getResources(), UserHandle.myUserId(), false);
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon);
        }
        Drawable drawableUserIcon = new CircleFramedDrawable(bitmapUserIcon,
                (int) getContext().getResources().getDimension(R.dimen.circle_avatar_size));

        return drawableUserIcon;
    }

    @VisibleForTesting
    EditUserPhotoController createEditUserPhotoController(ImageView userPhotoView,
                Drawable drawable) {
        return new EditUserPhotoController(mFragment, userPhotoView,
                mSavedPhoto, drawable, mWaitingForActivityResult);
    }

    public static class EmergencyNamePreferenceDialogFragment extends
            CustomPreferenceDialogFragment {

        public static CustomDialogPreference.CustomPreferenceDialogFragment newInstance(
                String key) {
            final CustomDialogPreference.CustomPreferenceDialogFragment
                    fragment = new EmergencyNamePreferenceDialogFragment();
            final Bundle b = new Bundle(1 /* capacity */);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        private EmergencyNamePreference getEmergencyNamePreference() {
            return (EmergencyNamePreference) getPreference();
        }

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            getEmergencyNamePreference().setFragment(this);
            if (icicle != null) {
                getEmergencyNamePreference().onRestoreInstanceState(icicle);
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            getEmergencyNamePreference().onSaveInstanceState(outState);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            getEmergencyNamePreference().onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            getEmergencyNamePreference().startingActivityForResult();
            super.startActivityForResult(intent, requestCode);
        }
    }
}
