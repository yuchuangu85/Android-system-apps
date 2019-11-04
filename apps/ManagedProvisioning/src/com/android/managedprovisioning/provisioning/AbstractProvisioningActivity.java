/*
 * Copyright 2019, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import android.annotation.IntDef;
import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.DialogBuilder;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Abstract class for provisioning activity.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
// TODO(b/123288153): Rearrange provisioning activity, manager, controller classes.
public abstract class AbstractProvisioningActivity extends SetupGlifLayoutActivity
        implements SimpleDialog.SimpleDialogListener, ProvisioningManagerCallback {

    private static final String KEY_ACTIVITY_STATE = "activity-state";

    static final int STATE_PROVISIONING_INTIIALIZING = 1;
    static final int STATE_PROVISIONING_STARTED = 2;
    static final int STATE_PROVISIONING_FINALIZED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_PROVISIONING_INTIIALIZING,
            STATE_PROVISIONING_STARTED,
            STATE_PROVISIONING_FINALIZED})
    private @interface ProvisioningState {}

    private static final String ERROR_DIALOG_OK = "ErrorDialogOk";
    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";
    private static final String CANCEL_PROVISIONING_DIALOG_OK = "CancelProvisioningDialogOk";
    private static final String CANCEL_PROVISIONING_DIALOG_RESET =
            "CancelProvisioningDialogReset";

    protected ProvisioningManagerInterface mProvisioningManager;
    protected ProvisioningParams mParams;
    protected @ProvisioningState int mState;

    @VisibleForTesting
    protected AbstractProvisioningActivity(Utils utils) {
        super(utils);
    }

    // Lazily initialize ProvisioningManager, since we can't call in ProvisioningManager.getInstance
    // in constructor as base context is not available in constructor.
    protected abstract ProvisioningManagerInterface getProvisioningManager();
    // Show the dialog when user press back button while provisioning.
    protected abstract void decideCancelProvisioningDialog();
    // Initialize UI for this activity.
    protected abstract void initializeUi(ProvisioningParams params);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi(mParams);

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(KEY_ACTIVITY_STATE,
                    STATE_PROVISIONING_INTIIALIZING);
        } else {
            mState = STATE_PROVISIONING_INTIIALIZING;
        }

        if (mState == STATE_PROVISIONING_INTIIALIZING) {
            getProvisioningManager().maybeStartProvisioning(mParams);
            mState = STATE_PROVISIONING_STARTED;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIVITY_STATE, mState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAnyDialogAdded()) {
            getProvisioningManager().registerListener(this);
        }
    }

    private boolean isAnyDialogAdded() {
        return isDialogAdded(ERROR_DIALOG_OK)
                || isDialogAdded(ERROR_DIALOG_RESET)
                || isDialogAdded(CANCEL_PROVISIONING_DIALOG_OK)
                || isDialogAdded(CANCEL_PROVISIONING_DIALOG_RESET);
    }

    @Override
    public void onPause() {
        getProvisioningManager().unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // if EXTRA_PROVISIONING_SKIP_USER_CONSENT is specified, don't allow user to cancel
        if (mParams.skipUserConsent) {
            return;
        }

        decideCancelProvisioningDialog();
    }

    protected void showCancelProvisioningDialog(boolean resetRequired) {
        if (resetRequired) {
            showDialog(mUtils.createCancelProvisioningResetDialogBuilder(),
                    CANCEL_PROVISIONING_DIALOG_RESET);
        } else {
            showDialog(mUtils.createCancelProvisioningDialogBuilder(),
                   CANCEL_PROVISIONING_DIALOG_OK);
        }
    }

    @Override
    public void error(int titleId, int messageId, boolean resetRequired) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(resetRequired
                        ? R.string.reset : android.R.string.ok);

        showDialog(dialogBuilder, resetRequired ? ERROR_DIALOG_RESET : ERROR_DIALOG_OK);
    }

    @Override
    protected void showDialog(DialogBuilder builder, String tag) {
        // Whenever a dialog is shown, stop listening for further updates
        getProvisioningManager().unregisterListener(this);
        super.showDialog(builder, tag);
    }

    private void onProvisioningAborted() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
            case CANCEL_PROVISIONING_DIALOG_RESET:
                dialog.dismiss();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
        getProvisioningManager().registerListener(this);
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
                getProvisioningManager().cancelProvisioning();
                onProvisioningAborted();
                break;
            case CANCEL_PROVISIONING_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "Provisioning cancelled by user");
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_OK:
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "Error during provisioning");
                onProvisioningAborted();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }
}
