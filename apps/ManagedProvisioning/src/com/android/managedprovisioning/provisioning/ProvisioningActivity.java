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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;

import android.Manifest.permission;
import android.annotation.IntDef;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.FinalizationController;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.AnimationComponents;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationCallback;
import com.android.managedprovisioning.transition.TransitionActivity;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupcompat.util.WizardManagerHelper;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends AbstractProvisioningActivity
        implements TransitionAnimationCallback {
    private static final int POLICY_COMPLIANCE_REQUEST_CODE = 1;
    private static final int TRANSITION_ACTIVITY_REQUEST_CODE = 2;
    private static final int RESULT_CODE_ADD_PERSONAL_ACCOUNT = 120;

    static final int PROVISIONING_MODE_WORK_PROFILE = 1;
    static final int PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 2;
    static final int PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE = 3;

    @IntDef(prefix = { "PROVISIONING_MODE_" }, value = {
        PROVISIONING_MODE_WORK_PROFILE,
        PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ProvisioningMode {}

    private static final Map<Integer, Integer> PROVISIONING_MODE_TO_PROGRESS_LABEL =
            Collections.unmodifiableMap(new HashMap<Integer, Integer>() {{
                put(PROVISIONING_MODE_WORK_PROFILE,
                        R.string.work_profile_provisioning_progress_label);
                put(PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                        R.string.fully_managed_device_provisioning_progress_label);
                put(PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE,
                        R.string.fully_managed_device_provisioning_progress_label);
            }});

    private TransitionAnimationHelper mTransitionAnimationHelper;
    private RepeatingVectorAnimation mRepeatingVectorAnimation;
    private FooterButton mNextButton;
    private UserProvisioningStateHelper mUserProvisioningStateHelper;
    private DevicePolicyManager mDevicePolicyManager;

    public ProvisioningActivity() {
        super(new Utils());
    }

    @VisibleForTesting
    public ProvisioningActivity(ProvisioningManager provisioningManager, Utils utils,
                UserProvisioningStateHelper userProvisioningStateHelper) {
        super(utils);
        mProvisioningManager = provisioningManager;
        mUserProvisioningStateHelper = userProvisioningStateHelper;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mUserProvisioningStateHelper == null) {
            mUserProvisioningStateHelper = new UserProvisioningStateHelper(this);
        }
        mDevicePolicyManager = getSystemService(DevicePolicyManager.class);
    }

    @Override
    protected ProvisioningManagerInterface getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = ProvisioningManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @Override
    public void preFinalizationCompleted() {
        if (mState == STATE_PROVISIONING_FINALIZED) {
            return;
        }

        ProvisionLogger.logi("ProvisioningActivity pre-finalization completed");

        // TODO: call this for the new flow after new NFC flow has been added
        // maybeLaunchNfcUserSetupCompleteIntent();

        if (mParams.skipEducationScreens || mTransitionAnimationHelper.areAllTransitionsShown()) {
            updateProvisioningFinalizedScreen();
        }
        mState = STATE_PROVISIONING_FINALIZED;
    }

    private void updateProvisioningFinalizedScreen() {
        if (!mParams.skipEducationScreens) {
            final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
            layout.findViewById(R.id.provisioning_progress).setVisibility(View.GONE);
            mNextButton.setVisibility(View.VISIBLE);
        }

        if (mParams.skipEducationScreens || Utils.isSilentProvisioning(this, mParams)) {
            onNextButtonClicked();
        }
    }

    private void onNextButtonClicked() {
        new FinalizationController(getApplicationContext(), mUserProvisioningStateHelper)
                .provisioningInitiallyDone(mParams);
        if (mUtils.isAdminIntegratedFlow(mParams)) {
            enableGlobalFlags();
            showPolicyComplianceScreen();
        } else {
            finishProvisioning();
        }
    }

    private void enableGlobalFlags() {
        if (mParams.isCloudEnrollment) {
            mDevicePolicyManager.setDeviceProvisioningConfigApplied();
        }
        final SettingsFacade settingsFacade = new SettingsFacade();
        settingsFacade.setUserSetupCompleted(this, UserHandle.USER_SYSTEM);
        settingsFacade.setDeviceProvisioned(this);
    }

    private void finishProvisioning() {
        setResult(Activity.RESULT_OK);
        maybeLaunchNfcUserSetupCompleteIntent();
        finish();
    }

    private void showPolicyComplianceScreen() {
        final String adminPackage = mParams.inferDeviceAdminPackageName();
        UserHandle userHandle;
        if (mParams.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
          userHandle = mUtils.getManagedProfile(getApplicationContext());
        } else {
          userHandle = UserHandle.of(UserHandle.myUserId());
        }

        final Intent policyComplianceIntent =
            new Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE);
        policyComplianceIntent.putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                mParams.adminExtrasBundle);
        policyComplianceIntent.setPackage(adminPackage);
        startActivityForResultAsUser(
            policyComplianceIntent, POLICY_COMPLIANCE_REQUEST_CODE, userHandle);
    }

    boolean shouldShowTransitionScreen() {
        return mParams.isOrganizationOwnedProvisioning
                && mParams.provisioningMode == ProvisioningParams.PROVISIONING_MODE_MANAGED_PROFILE
                && mUtils.isConnectedToNetwork(getApplicationContext());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case POLICY_COMPLIANCE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    if (shouldShowTransitionScreen()) {
                        Intent intent = new Intent(this, TransitionActivity.class);
                        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
                        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
                        startActivityForResult(intent, TRANSITION_ACTIVITY_REQUEST_CODE);
                    } else {
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                } else {
                    error(/* titleId */ R.string.cant_set_up_device,
                            /* messageId */ R.string.contact_your_admin_for_help,
                            /* resetRequired = */ true);
                }
                break;
            case TRANSITION_ACTIVITY_REQUEST_CODE:
                setResult(RESULT_CODE_ADD_PERSONAL_ACCOUNT);
                finish();
                break;
        }
    }

    private void maybeLaunchNfcUserSetupCompleteIntent() {
        if (mParams != null && mParams.isNfc) {
            // Start SetupWizard to complete the intent.
            final Intent intent = new Intent(DevicePolicyManager.ACTION_STATE_USER_SETUP_COMPLETE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final PackageManager pm = getPackageManager();
            List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);

            // Look for the first legitimate component protected by the permission
            ComponentName targetComponent = null;
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) {
                    continue;
                }
                if (!permission.BIND_DEVICE_ADMIN.equals(ri.activityInfo.permission)) {
                    ProvisionLogger.loge("Component " + ri.activityInfo.getComponentName()
                            + " is not protected by " + permission.BIND_DEVICE_ADMIN);
                } else if (pm.checkPermission(permission.DISPATCH_PROVISIONING_MESSAGE,
                        ri.activityInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    ProvisionLogger.loge("Package " + ri.activityInfo.packageName
                            + " does not have " + permission.DISPATCH_PROVISIONING_MESSAGE);
                } else {
                    targetComponent = ri.activityInfo.getComponentName();
                    break;
                }
            }

            if (targetComponent == null) {
                ProvisionLogger.logw("No activity accepts intent ACTION_STATE_USER_SETUP_COMPLETE");
                return;
            }

            intent.setComponent(targetComponent);
            startActivity(intent);
            ProvisionLogger.logi("Launched ACTION_STATE_USER_SETUP_COMPLETE with component "
                    + targetComponent);
        }
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        if (mState == STATE_PROVISIONING_FINALIZED && !mParams.isOrganizationOwnedProvisioning) {
            return;
        }

        if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)
                || mParams.isOrganizationOwnedProvisioning) {
            showCancelProvisioningDialog(/* resetRequired = */true);
        } else {
            showCancelProvisioningDialog(/* resetRequired = */false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mParams.skipEducationScreens) {
            startSpinnerAnimation();
        } else {
            startTransitionAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mParams.skipEducationScreens) {
            endSpinnerAnimation();
        } else {
            endTransitionAnimation();
        }
    }

    @Override
    public void onAllTransitionsShown() {
        if (mState == STATE_PROVISIONING_FINALIZED) {
            updateProvisioningFinalizedScreen();
        }
    }

    @Override
    protected void initializeUi(ProvisioningParams params) {
        final boolean isPoProvisioning = mUtils.isProfileOwnerAction(params.provisioningAction);
        final int titleResId =
            isPoProvisioning ? R.string.setup_profile_progress : R.string.setup_device_progress;

        CustomizationParams customizationParams =
                CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(R.layout.provisioning_progress, null, customizationParams);
        setTitle(titleResId);

        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        setupEducationViews(layout);

        mNextButton = Utils.addNextButton(layout, v -> onNextButtonClicked());
        mNextButton.setVisibility(View.INVISIBLE);

        handleSupportUrl(layout, customizationParams);
    }

    private void setupEducationViews(GlifLayout layout) {
        final TextView header = layout.findViewById(R.id.suc_layout_title);
        header.setTextColor(getColorStateList(R.color.header_text_color));

        final int progressLabelResId =
                PROVISIONING_MODE_TO_PROGRESS_LABEL.get(getProvisioningMode());
        final TextView progressLabel = layout.findViewById(R.id.provisioning_progress);
        if (mParams.skipEducationScreens) {
            header.setText(progressLabelResId);
            progressLabel.setVisibility(View.INVISIBLE);
            layout.findViewById(R.id.subheader).setVisibility(View.INVISIBLE);
            layout.findViewById(R.id.provider_info).setVisibility(View.INVISIBLE);
        } else {
            progressLabel.setText(progressLabelResId);
            progressLabel.setVisibility(View.VISIBLE);
        }
    }

    private void setupTransitionAnimationHelper(GlifLayout layout) {
        final TextView header = layout.findViewById(R.id.suc_layout_title);
        final TextView subHeader = layout.findViewById(R.id.subheader);
        final ImageView drawable = layout.findViewById(R.id.animation);
        final TextView providerInfo = layout.findViewById(R.id.provider_info);
        final int provisioningMode = getProvisioningMode();
        final AnimationComponents animationComponents =
                new AnimationComponents(header, subHeader, drawable, providerInfo);
        mTransitionAnimationHelper =
                new TransitionAnimationHelper(provisioningMode, animationComponents, this);
    }

    private @ProvisioningMode int getProvisioningMode() {
        int provisioningMode = 0;
        final boolean isProfileOwnerAction =
                mUtils.isProfileOwnerAction(mParams.provisioningAction);
        if (isProfileOwnerAction) {
            if (getSystemService(DevicePolicyManager.class).isDeviceManaged()) {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE;
            } else {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE;
            }
        } else if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
            provisioningMode = PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
        }
        return provisioningMode;
    }

    private void handleSupportUrl(GlifLayout layout, CustomizationParams customization) {
        final TextView info = layout.findViewById(R.id.provider_info);
        final String deviceProvider = getString(R.string.organization_admin);
        final String contactDeviceProvider =
                getString(R.string.contact_device_provider, deviceProvider);
        final ClickableSpanFactory spanFactory =
                new ClickableSpanFactory(getColor(R.color.blue_text));
        mUtils.handleSupportUrl(this, customization, spanFactory,
                new AccessibilityContextMenuMaker(this), info, deviceProvider,
                contactDeviceProvider);
    }

    private void startTransitionAnimation() {
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        setupTransitionAnimationHelper(layout);
        mTransitionAnimationHelper.start();
    }

    private void endTransitionAnimation() {
        mTransitionAnimationHelper.clean();
        mTransitionAnimationHelper = null;
    }

    private void startSpinnerAnimation() {
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        final ImageView animation = layout.findViewById(R.id.animation);
        if (animation.getVisibility() == View.INVISIBLE) {
            return;
        }
        animation.setImageResource(R.drawable.enterprise_wp_animation);
        final AnimatedVectorDrawable vectorDrawable =
            (AnimatedVectorDrawable) animation.getDrawable();
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(vectorDrawable);
        mRepeatingVectorAnimation.start();
    }

    private void endSpinnerAnimation() {
        if (mRepeatingVectorAnimation ==  null) {
            return;
        }
        mRepeatingVectorAnimation.stop();
        mRepeatingVectorAnimation = null;
    }
}