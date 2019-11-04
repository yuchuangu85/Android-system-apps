/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE;
import static android.app.admin.DevicePolicyManager.ACTION_GET_PROVISIONING_MODE;

import static com.android.managedprovisioning.model.ProvisioningParams.PROVISIONING_MODE_FULLY_MANAGED_DEVICE_LEGACY;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.LogoUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.PreProvisioningController.UiParams;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelperFactory;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelper;
import com.android.managedprovisioning.preprovisioning.consent.ConsentUiHelperCallback;
import com.android.managedprovisioning.provisioning.LandingActivity;
import com.android.managedprovisioning.provisioning.ProvisioningActivity;
import com.google.android.setupcompat.util.WizardManagerHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class PreProvisioningActivity extends SetupGlifLayoutActivity implements
        SimpleDialog.SimpleDialogListener, PreProvisioningController.Ui, ConsentUiHelperCallback {

    private static final String KEY_ACTIVITY_STATE = "activity-state";

    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 1;
    @VisibleForTesting
    protected static final int PROVISIONING_REQUEST_CODE = 2;
    private static final int WIFI_REQUEST_CODE = 3;
    private static final int CHANGE_LAUNCHER_REQUEST_CODE = 4;
    private static final int ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE = 5;
    private static final int GET_PROVISIONING_MODE_REQUEST_CODE = 6;

    // Note: must match the constant defined in HomeSettings
    private static final String EXTRA_SUPPORT_MANAGED_PROFILES = "support_managed_profiles";
    private static final String SAVED_PROVISIONING_PARAMS = "saved_provisioning_params";

    private static final String ERROR_AND_CLOSE_DIALOG = "PreProvErrorAndCloseDialog";
    private static final String BACK_PRESSED_DIALOG_RESET = "PreProvBackPressedDialogReset";
    private static final String BACK_PRESSED_DIALOG_CLOSE_ACTIVITY =
            "PreProvBackPressedDialogCloseActivity";
    private static final String LAUNCHER_INVALID_DIALOG = "PreProvCurrentLauncherInvalidDialog";
    private static final String DELETE_MANAGED_PROFILE_DIALOG = "PreProvDeleteManagedProfileDialog";

    private PreProvisioningController mController;
    private ControllerProvider mControllerProvider;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private ConsentUiHelper mConsentUiHelper;

    static final int STATE_PREPROVISIONING_INTIIALIZING = 1;
    static final int STATE_PROVISIONING_STARTED = 2;
    static final int STATE_PROVISIONING_FINALIZED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_PREPROVISIONING_INTIIALIZING,
            STATE_PROVISIONING_STARTED,
            STATE_PROVISIONING_FINALIZED})
    private @interface PreProvisioningState {}

    private @PreProvisioningState int mState;

    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";

    public PreProvisioningActivity() {
        this(activity -> new PreProvisioningController(activity, activity), null, new Utils());
    }

    @VisibleForTesting
    public PreProvisioningActivity(ControllerProvider controllerProvider,
            AccessibilityContextMenuMaker contextMenuMaker, Utils utils) {
        super(utils);
        mControllerProvider = controllerProvider;
        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mState = savedInstanceState == null
                ? STATE_PREPROVISIONING_INTIIALIZING
                : savedInstanceState.getInt(KEY_ACTIVITY_STATE, STATE_PREPROVISIONING_INTIIALIZING);

        mController = mControllerProvider.getInstance(this);
        mConsentUiHelper = ConsentUiHelperFactory.getInstance(
                /* activity */ this, /* contextMenuMaker */ mContextMenuMaker,
                /* callback */ this, /* utils */ mUtils, mController.getSettingsFacade());
        if (mState == STATE_PREPROVISIONING_INTIIALIZING) {
            ProvisioningParams params = savedInstanceState == null ? null
                    : savedInstanceState.getParcelable(SAVED_PROVISIONING_PARAMS);
            mController.initiateProvisioning(getIntent(), params, getCallingPackage());
        }
    }

    @Override
    public void finish() {
        // The user has backed out of provisioning, so we perform the necessary clean up steps.
        LogoUtils.cleanUp(this);
        ProvisioningParams params = mController.getParams();
        if (params != null) {
            params.cleanUp();
        }
        EncryptionController.getInstance(this).cancelEncryptionReminder();
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SAVED_PROVISIONING_PARAMS, mController.getParams());
        outState.putInt(KEY_ACTIVITY_STATE, mState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ENCRYPT_DEVICE_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled device encryption.");
                }
                break;
            case PROVISIONING_REQUEST_CODE:
                mState = STATE_PROVISIONING_FINALIZED;
                setResult(resultCode);
                finish();
                break;
            case CHANGE_LAUNCHER_REQUEST_CODE:
                mController.continueProvisioningAfterUserConsent();
                break;
            case WIFI_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled wifi picking.");
                    setResult(resultCode);
                    finish();
                } else {
                    if (resultCode == RESULT_OK) {
                        ProvisionLogger.logd("Wifi request result is OK");
                    }
                    mController.initiateProvisioning(getIntent(), null /* cached params */,
                            getCallingPackage());
                }
                break;
            case ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    maybeShowAdminGetProvisioningModeScreen();
                } else {
                    setResult(resultCode);
                    finish();
                }
                break;
            case GET_PROVISIONING_MODE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    if(data != null && mController.updateProvisioningParamsFromIntent(data)) {
                        mController.showUserConsentScreen();
                    } else {
                        showFactoryResetDialog(R.string.cant_set_up_device,
                                R.string.contact_your_admin_for_help);
                    }
                } else {
                    showFactoryResetDialog(R.string.cant_set_up_device,
                            R.string.contact_your_admin_for_help);
                }
                break;
            default:
                ProvisionLogger.logw("Unknown result code :" + resultCode);
                break;
        }
    }

    @Override
    public void showErrorAndClose(Integer titleId, int messageId, String logText) {
        ProvisionLogger.loge(logText);

        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(R.string.device_owner_error_ok);
        showDialog(dialogBuilder, ERROR_AND_CLOSE_DIALOG);
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case BACK_PRESSED_DIALOG_CLOSE_ACTIVITY:
            case BACK_PRESSED_DIALOG_RESET:
                // user chose to continue. Do nothing
                break;
            case LAUNCHER_INVALID_DIALOG:
                dialog.dismiss();
                break;
            case DELETE_MANAGED_PROFILE_DIALOG:
                setResult(Activity.RESULT_CANCELED);
                finish();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case ERROR_AND_CLOSE_DIALOG:
            case BACK_PRESSED_DIALOG_CLOSE_ACTIVITY:
                onProvisioningAborted();
                break;
            case BACK_PRESSED_DIALOG_RESET:
                mUtils.sendFactoryResetBroadcast(this,
                        "Provisioning cancelled by user on consent screen");
                onProvisioningAborted();
                break;
            case LAUNCHER_INVALID_DIALOG:
                requestLauncherPick();
                break;
            case DELETE_MANAGED_PROFILE_DIALOG:
                DeleteManagedProfileDialog d = (DeleteManagedProfileDialog) dialog;
                mController.removeUser(d.getUserId());
                mController.initiateProvisioning(getIntent(), /* cached params */ null,
                        getCallingPackage());
                break;
            case ERROR_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "Error during preprovisioning");
                setResult(Activity.RESULT_CANCELED);
                finish();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void onProvisioningAborted() {
        setResult(Activity.RESULT_CANCELED);
        mController.logPreProvisioningCancelled();
        finish();
    }

    @Override
    public void requestEncryption(ProvisioningParams params) {
        Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class);
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), encryptIntent);
        encryptIntent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
    }

    @Override
    public void requestWifiPick() {
        final Intent intent = mUtils.getWifiPickIntent();
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        startActivityForResult(intent, WIFI_REQUEST_CODE);
    }

    @Override
    public void showCurrentLauncherInvalid() {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setTitle(R.string.change_device_launcher)
                .setMessage(R.string.launcher_app_cant_be_used_by_work_profile)
                .setNegativeButtonMessage(R.string.cancel_provisioning)
                .setPositiveButtonMessage(R.string.pick_launcher);
        showDialog(dialogBuilder, LAUNCHER_INVALID_DIALOG);
    }

    private void requestLauncherPick() {
        Intent changeLauncherIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
        changeLauncherIntent.putExtra(EXTRA_SUPPORT_MANAGED_PROFILES, true);
        startActivityForResult(changeLauncherIntent, CHANGE_LAUNCHER_REQUEST_CODE);
    }

    public void startProvisioning(int userId, ProvisioningParams params) {
        mState = STATE_PROVISIONING_STARTED;
        Intent intent = new Intent(this, ProvisioningActivity.class);
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        startActivityForResultAsUser(intent, PROVISIONING_REQUEST_CODE, new UserHandle(userId));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void maybeShowAdminGetProvisioningModeScreen() {
        final String adminPackage = mController.getParams().inferDeviceAdminPackageName();
        final Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(adminPackage);
        final Intent intentPolicy = new Intent(ACTION_ADMIN_POLICY_COMPLIANCE);
        intentPolicy.setPackage(adminPackage);
        final ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (!activityManager.isLowRamDevice()
                && !mController.getParams().isNfc
                && intentGetMode.resolveActivity(getPackageManager()) != null
                && intentPolicy.resolveActivity(getPackageManager()) != null) {
            mController.putExtrasIntoGetModeIntent(intentGetMode);
            startActivityForResult(intentGetMode, GET_PROVISIONING_MODE_REQUEST_CODE);
        } else {
            startManagedDeviceLegacyFlow();
        }
    }

    private void startManagedDeviceLegacyFlow() {
        mController.setProvisioningMode(PROVISIONING_MODE_FULLY_MANAGED_DEVICE_LEGACY);
        mController.showUserConsentScreen();
    }

    @Override
    public void showFactoryResetDialog(Integer titleId, int messageId) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(R.string.reset);

        showDialog(dialogBuilder, ERROR_DIALOG_RESET);
    }

    @Override
    public void initiateUi(UiParams uiParams) {
        mConsentUiHelper.initiateUi(uiParams);
    }

    @Override
    public void prepareAdminIntegratedFlow(ProvisioningParams params) {
        Intent intent = new Intent(this, LandingActivity.class);
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        startActivityForResult(intent, ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    @Override
    public void showDeleteManagedProfileDialog(ComponentName mdmPackageName, String domainName,
            int userId) {
        showDialog(() -> DeleteManagedProfileDialog.newInstance(userId,
                mdmPackageName, domainName), DELETE_MANAGED_PROFILE_DIALOG);
    }

    @Override
    public void onBackPressed() {
        if (mController.getParams().isOrganizationOwnedProvisioning) {
            showDialog(mUtils.createCancelProvisioningResetDialogBuilder(),
                    BACK_PRESSED_DIALOG_RESET);
        } else {
            showDialog(mUtils.createCancelProvisioningDialogBuilder(),
                    BACK_PRESSED_DIALOG_CLOSE_ACTIVITY);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mConsentUiHelper.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mConsentUiHelper.onStop();
    }

    @Override
    public void nextAfterUserConsent() {
        mController.continueProvisioningAfterUserConsent();
    }

    @Override
    public void initializeLayoutParams(int layoutResourceId, @Nullable Integer headerResourceId,
            CustomizationParams params) {
        super.initializeLayoutParams(layoutResourceId, headerResourceId, params);
    }

    /**
     * Constructs {@link PreProvisioningController} for a given {@link PreProvisioningActivity}
     */
    interface ControllerProvider {
        /**
         * Constructs {@link PreProvisioningController} for a given {@link PreProvisioningActivity}
         */
        PreProvisioningController getInstance(PreProvisioningActivity activity);
    }
}