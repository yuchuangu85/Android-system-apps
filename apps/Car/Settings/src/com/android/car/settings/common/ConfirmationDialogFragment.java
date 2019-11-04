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

package com.android.car.settings.common;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

/**
 * Common dialog that can be used across the settings app to ask the user to confirm their desired
 * action.
 */
public class ConfirmationDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    /** Builder to help construct {@link ConfirmationDialogFragment}. */
    public static class Builder {

        private final Context mContext;
        private Bundle mArgs;
        private String mTitle;
        private String mMessage;
        private String mPosLabel;
        private String mNegLabel;
        private ConfirmListener mConfirmListener;
        private RejectListener mRejectListener;

        public Builder(Context context) {
            mContext = context;
        }

        /** Sets the title. */
        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        /** Sets the title. */
        public Builder setTitle(@StringRes int title) {
            mTitle = mContext.getString(title);
            return this;
        }

        /** Sets the message. */
        public Builder setMessage(String message) {
            mMessage = message;
            return this;
        }

        /** Sets the message. */
        public Builder setMessage(@StringRes int message) {
            mMessage = mContext.getString(message);
            return this;
        }

        /** Sets the positive button label. */
        public Builder setPositiveButton(String label, ConfirmListener confirmListener) {
            mPosLabel = label;
            mConfirmListener = confirmListener;
            return this;
        }

        /** Sets the positive button label. */
        public Builder setPositiveButton(@StringRes int label, ConfirmListener confirmListener) {
            mPosLabel = mContext.getString(label);
            mConfirmListener = confirmListener;
            return this;
        }

        /** Sets the negative button label. */
        public Builder setNegativeButton(String label, RejectListener rejectListener) {
            mNegLabel = label;
            mRejectListener = rejectListener;
            return this;
        }

        /** Sets the negative button label. */
        public Builder setNegativeButton(@StringRes int label, RejectListener rejectListener) {
            mNegLabel = mContext.getString(label);
            mRejectListener = rejectListener;
            return this;
        }

        /** Adds an argument string to the argument bundle. */
        public Builder addArgumentString(String argumentKey, String argument) {
            if (mArgs == null) {
                mArgs = new Bundle();
            }
            mArgs.putString(argumentKey, argument);
            return this;
        }

        /** Adds an argument boolean to the argument bundle. */
        public Builder addArgumentBoolean(String argumentKey, boolean argument) {
            if (mArgs == null) {
                mArgs = new Bundle();
            }
            mArgs.putBoolean(argumentKey, argument);
            return this;
        }

        /** Adds an argument Parcelable to the argument bundle. */
        public Builder addArgumentParcelable(String argumentKey, Parcelable argument) {
            if (mArgs == null) {
                mArgs = new Bundle();
            }
            mArgs.putParcelable(argumentKey, argument);
            return this;
        }

        /** Constructs the {@link ConfirmationDialogFragment}. */
        public ConfirmationDialogFragment build() {
            return ConfirmationDialogFragment.init(this);
        }
    }

    /** Identifier used to launch the dialog fragment. */
    public static final String TAG = "ConfirmationDialogFragment";

    // Argument keys are prefixed with TAG in order to reduce the changes of collision with user
    // provided arguments.
    private static final String ALL_ARGUMENTS_KEY = TAG + "_all_arguments";
    private static final String ARGUMENTS_KEY = TAG + "_arguments";
    private static final String TITLE_KEY = TAG + "_title";
    private static final String MESSAGE_KEY = TAG + "_message";
    private static final String POSITIVE_KEY = TAG + "_positive";
    private static final String NEGATIVE_KEY = TAG + "_negative";

    private String mTitle;
    private String mMessage;
    private String mPosLabel;
    private String mNegLabel;
    private ConfirmListener mConfirmListener;
    private RejectListener mRejectListener;

    /** Constructs the dialog fragment from the arguments provided in the {@link Builder} */
    private static ConfirmationDialogFragment init(Builder builder) {
        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putBundle(ARGUMENTS_KEY, builder.mArgs);
        args.putString(TITLE_KEY, builder.mTitle);
        args.putString(MESSAGE_KEY, builder.mMessage);
        args.putString(POSITIVE_KEY, builder.mPosLabel);
        args.putString(NEGATIVE_KEY, builder.mNegLabel);
        dialogFragment.setArguments(args);
        dialogFragment.setConfirmListener(builder.mConfirmListener);
        dialogFragment.setRejectListener(builder.mRejectListener);
        return dialogFragment;
    }

    /**
     * Since it is possible for the listeners to be unregistered on configuration change, provide a
     * way to reattach the listeners.
     */
    public static void resetListeners(@Nullable ConfirmationDialogFragment dialogFragment,
            @Nullable ConfirmListener confirmListener, @Nullable RejectListener rejectListener) {
        if (dialogFragment != null) {
            dialogFragment.setConfirmListener(confirmListener);
            dialogFragment.setRejectListener(rejectListener);
        }
    }

    /** Sets the listener which listens to a click on the positive button. */
    private void setConfirmListener(ConfirmListener confirmListener) {
        mConfirmListener = confirmListener;
    }

    /** Gets the listener which listens to a click on the positive button */
    @Nullable
    public ConfirmListener getConfirmListener() {
        return mConfirmListener;
    }

    /** Sets the listener which listens to a click on the negative button. */
    private void setRejectListener(RejectListener rejectListener) {
        mRejectListener = rejectListener;
    }

    /** Gets the listener which listens to a click on the negative button. */
    @Nullable
    public RejectListener getRejectListener() {
        return mRejectListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (savedInstanceState != null) {
            args = savedInstanceState.getBundle(ALL_ARGUMENTS_KEY);
        }

        if (args != null) {
            mTitle = getArguments().getString(TITLE_KEY);
            mMessage = getArguments().getString(MESSAGE_KEY);
            mPosLabel = getArguments().getString(POSITIVE_KEY);
            mNegLabel = getArguments().getString(NEGATIVE_KEY);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(ALL_ARGUMENTS_KEY, getArguments());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        if (!TextUtils.isEmpty(mTitle)) {
            builder.setTitle(mTitle);
        }
        if (!TextUtils.isEmpty(mMessage)) {
            builder.setMessage(mMessage);
        }
        if (!TextUtils.isEmpty(mPosLabel)) {
            builder.setPositiveButton(mPosLabel, this);
        }
        if (!TextUtils.isEmpty(mNegLabel)) {
            builder.setNegativeButton(mNegLabel, this);
        }
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (mConfirmListener != null) {
                mConfirmListener.onConfirm(getArguments().getBundle(ARGUMENTS_KEY));
            }
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            if (mRejectListener != null) {
                mRejectListener.onReject(getArguments().getBundle(ARGUMENTS_KEY));
            }
        }
    }

    /** Listens to the confirmation action. */
    public interface ConfirmListener {
        /**
         * Defines the action to take on confirm. The bundle will contain the arguments added when
         * constructing the dialog through with {@link Builder#addArgumentString(String, String)}.
         */
        void onConfirm(@Nullable Bundle arguments);
    }

    /** Listens to the rejection action. */
    public interface RejectListener {
        /**
         * Defines the action to take on reject. The bundle will contain the arguments added when
         * constructing the dialog through with {@link Builder#addArgumentString(String, String)}.
         */
        void onReject(@Nullable Bundle arguments);
    }
}
