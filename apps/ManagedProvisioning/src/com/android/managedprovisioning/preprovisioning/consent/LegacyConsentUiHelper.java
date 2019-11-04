/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.consent;

import static com.android.internal.util.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StringConcatenator;
import com.android.managedprovisioning.common.TouchTargetEnforcer;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.preprovisioning.PreProvisioningController.UiParams;
import com.android.managedprovisioning.preprovisioning.anim.BenefitsAnimation;
import com.google.android.setupdesign.GlifLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements functionality for the legacy consent screen.
 * <p>Android Auto still uses the old UI, so we need to ensure compatibility with it.
 */
class LegacyConsentUiHelper implements ConsentUiHelper {
    private static final List<Integer> SLIDE_CAPTIONS = createImmutableList(
        R.string.info_anim_title_0,
        R.string.info_anim_title_1,
        R.string.info_anim_title_2);
    private static final List<Integer> SLIDE_CAPTIONS_COMP = createImmutableList(
        R.string.info_anim_title_0,
        R.string.one_place_for_work_apps,
        R.string.info_anim_title_2);

    private final Activity mActivity;
    private final ClickableSpanFactory mClickableSpanFactory;
    private final TouchTargetEnforcer mTouchTargetEnforcer;
    private final ConsentUiHelperCallback mCallback;
    private final Utils mUtils;
    private @Nullable BenefitsAnimation mBenefitsAnimation;
    private final AccessibilityContextMenuMaker mContextMenuMaker;

    LegacyConsentUiHelper(Activity activity, AccessibilityContextMenuMaker contextMenuMaker,
            ConsentUiHelperCallback callback, Utils utils) {
        mActivity = checkNotNull(activity);
        mContextMenuMaker = contextMenuMaker;
        mCallback = callback;
        mTouchTargetEnforcer =
            new TouchTargetEnforcer(activity.getResources().getDisplayMetrics().density);
        mClickableSpanFactory = new ClickableSpanFactory(mActivity.getColor(R.color.blue_text));
        mUtils = utils;
    }

    @Override
    public void onStart() {
        if (mBenefitsAnimation != null) {
            mBenefitsAnimation.start();
        }
    }

    @Override
    public void onStop() {
        if (mBenefitsAnimation != null) {
            mBenefitsAnimation.stop();
        }
    }

    private void setDpcIconAndLabel(@NonNull String appName, Drawable packageIcon, String orgName) {
        if (packageIcon == null || TextUtils.isEmpty(appName)) {
            return;
        }

        // make a container with all parts of DPC app description visible
        mActivity.findViewById(R.id.intro_device_owner_app_info_container)
            .setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(orgName)) {
            orgName = mActivity.getString(R.string.your_organization_beginning);
        }
        String message = mActivity.getString(R.string.your_org_app_used, orgName);
        TextView appInfoText = mActivity.findViewById(R.id.device_owner_app_info_text);
        appInfoText.setText(message);

        ImageView imageView = mActivity.findViewById(R.id.device_manager_icon_view);
        imageView.setImageDrawable(packageIcon);
        imageView.setContentDescription(
            mActivity.getResources().getString(R.string.mdm_icon_label, appName));

        TextView deviceManagerName = mActivity.findViewById(R.id.device_manager_name);
        deviceManagerName.setText(appName);
    }

    // TODO: refactor complex localized string assembly to an abstraction http://b/34288292
    // there is a bit of copy-paste, and some details easy to forget (e.g. setMovementMethod)
    private Spannable assembleDOTermsMessage(
            @NonNull String termsHeaders, String orgName, Intent showTermsIntent) {
        String linkText = mActivity.getString(R.string.view_terms);

        if (TextUtils.isEmpty(orgName)) {
            orgName = mActivity.getString(R.string.your_organization_middle);
        }
        String messageText = termsHeaders.isEmpty()
            ? mActivity.getString(R.string.device_owner_info, orgName, linkText)
            : mActivity
                .getString(R.string.device_owner_info_with_terms_headers, orgName, termsHeaders,
                    linkText);

        Spannable result = new SpannableString(messageText);
        int start = messageText.indexOf(linkText);

        ClickableSpan span = mClickableSpanFactory.create(showTermsIntent);
        result.setSpan(span, start, start + linkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    private static List<Integer> createImmutableList(int... values) {
        if (values == null || values.length == 0) {
            return emptyList();
        }
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return unmodifiableList(result);
    }

    @Override
    public void initiateUi(UiParams uiParams) {
        int layoutResId = 0;
        int titleResId = 0;
        int headerResId = 0;
        String packageLabel = null;
        Drawable packageIcon = null;
        if (mUtils.isProfileOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_profile;
            headerResId = R.string.work_profile_provisioning_accept_header;
            layoutResId = R.layout.intro_profile_owner;
        } else if (mUtils.isDeviceOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_device;
            headerResId = R.string.fully_managed_device_provisioning_accept_header;
            layoutResId = R.layout.intro_device_owner;
            packageLabel = uiParams.packageInfo != null
                ? uiParams.packageInfo.appLabel
                : uiParams.deviceAdminLabel != null
                        ? uiParams.deviceAdminLabel : uiParams.packageName;
            packageIcon = uiParams.packageInfo != null ? uiParams.packageInfo.packageIcon
                : getDeviceAdminIconDrawable(uiParams.deviceAdminIconFilePath);
        }

        final CustomizationParams customization = uiParams.customization;
        mCallback.initializeLayoutParams(
                layoutResId,
                headerResId,
                customization);

        setupAcceptAndContinueButton(uiParams.isSilentProvisioning);

        mActivity.setTitle(titleResId);

        final String headers =
            new StringConcatenator(mActivity.getResources()).join(uiParams.disclaimerHeadings);
        if (mUtils.isProfileOwnerAction(uiParams.provisioningAction)) {
            initiateUIProfileOwner(headers, uiParams.isDeviceManaged, customization,
                uiParams.viewTermsIntent);
        } else if (mUtils.isDeviceOwnerAction(uiParams.provisioningAction)){
            initiateUIDeviceOwner(packageLabel, packageIcon, headers, customization,
                uiParams.viewTermsIntent);
        }
    }

    private void setupAcceptAndContinueButton(boolean isSilentProvisioning) {
        final GlifLayout layout = mActivity.findViewById(R.id.setup_wizard_layout);
        Utils.addAcceptAndContinueButton(layout, v -> onNextButtonClicked());
        if (isSilentProvisioning) {
            onNextButtonClicked();
        }
    }

    private void onNextButtonClicked() {
        ProvisionLogger.logi("Next button (next_button) is clicked.");
        mCallback.nextAfterUserConsent();
    }

    private void initiateUIProfileOwner(String termsHeaders, boolean isComp,
        CustomizationParams customizationParams, Intent showTermsIntent) {
        // set up the back button
        Button backButton = mActivity.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            ProvisionLogger.logi("Back button (back_button) is clicked.");
            mActivity.onBackPressed();
        });

        int messageId = isComp ? R.string.profile_owner_info_comp : R.string.profile_owner_info;
        int messageWithTermsId = isComp ? R.string.profile_owner_info_with_terms_headers_comp
            : R.string.profile_owner_info_with_terms_headers;

        // set the short info text
        TextView shortInfo = mActivity.findViewById(R.id.profile_owner_short_info);
        shortInfo.setText(termsHeaders.isEmpty()
            ? mActivity.getString(messageId)
            : mActivity.getResources().getString(messageWithTermsId, termsHeaders));

        // set up show terms button
        View viewTermsButton = mActivity.findViewById(R.id.show_terms_button);
        viewTermsButton.setOnClickListener(v -> mActivity.startActivity(showTermsIntent));
        mTouchTargetEnforcer.enforce(viewTermsButton, (View) viewTermsButton.getParent());

        // show the intro animation
        mBenefitsAnimation = new BenefitsAnimation(
            mActivity,
            isComp
                ? SLIDE_CAPTIONS_COMP
                : SLIDE_CAPTIONS,
            isComp
                ? R.string.comp_profile_benefits_description
                : R.string.profile_benefits_description,
            customizationParams);
    }

    private void initiateUIDeviceOwner(
            String packageName, Drawable packageIcon, String termsHeaders,
        CustomizationParams customization, Intent showTermsIntent) {
        // set up the cancel button if it exists
        View cancelView = mActivity.findViewById(R.id.close_button);
        if (cancelView != null) {
            cancelView.setOnClickListener(v -> {
                ProvisionLogger.logi("Close button (close_button) is clicked.");
                mActivity.onBackPressed();
            });
        }

        // short terms info text with clickable 'view terms' link
        TextView shortInfoText = mActivity.findViewById(R.id.device_owner_terms_info);
        shortInfoText.setText(assembleDOTermsMessage(
            termsHeaders, customization.orgName, showTermsIntent));
        shortInfoText.setMovementMethod(LinkMovementMethod.getInstance()); // make clicks work
        mContextMenuMaker.registerWithActivity(shortInfoText);

        handleSupportUrl(customization);

        // set up DPC icon and label
        setDpcIconAndLabel(packageName, packageIcon, customization.orgName);
    }

    private void handleSupportUrl(CustomizationParams customization) {
        final TextView info = mActivity.findViewById(R.id.device_owner_provider_info);
        final String deviceProvider = mActivity.getString(R.string.organization_admin);
        final String contactDeviceProvider =
                mActivity.getString(R.string.contact_device_provider, deviceProvider);
        mUtils.handleSupportUrl(mActivity, customization, mClickableSpanFactory,
                mContextMenuMaker, info, deviceProvider, contactDeviceProvider);
    }

    private Drawable getDeviceAdminIconDrawable(@Nullable String deviceAdminIconFilePath) {
        if (deviceAdminIconFilePath == null) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(deviceAdminIconFilePath);
        if (bitmap == null) {
            return null;
        }
        return new BitmapDrawable(mActivity.getResources(), bitmap);
    }
}
