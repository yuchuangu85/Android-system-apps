/*
 * Copyright 2018 The Android Open Source Project
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

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.constraintlayout.widget.Guideline;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.car.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base fragment for all settings. Subclasses must provide a resource id via
 * {@link #getPreferenceScreenResId()} for the XML resource which defines the preferences to
 * display and controllers to update their state. This class is responsible for displaying the
 * preferences, creating {@link PreferenceController} instances from the metadata, and
 * associating the preferences with their corresponding controllers.
 *
 * <p>{@code preferenceTheme} must be specified in the application theme, and the parent to which
 * this fragment attaches must implement {@link UxRestrictionsProvider} and
 * {@link FragmentController} or an {@link IllegalStateException} will be thrown during
 * {@link #onAttach(Context)}. Changes to driving state restrictions are propagated to
 * controllers.
 */
public abstract class SettingsFragment extends PreferenceFragmentCompat implements
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener, FragmentController {

    @VisibleForTesting
    static final String DIALOG_FRAGMENT_TAG =
            "com.android.car.settings.common.SettingsFragment.DIALOG";

    private static final int MAX_NUM_PENDING_ACTIVITY_RESULT_CALLBACKS = 0xff - 1;

    private final Map<Class, List<PreferenceController>> mPreferenceControllersLookup =
            new ArrayMap<>();
    private final List<PreferenceController> mPreferenceControllers = new ArrayList<>();
    private final SparseArray<ActivityResultCallback> mActivityResultCallbackMap =
            new SparseArray<>();

    private CarUxRestrictions mUxRestrictions;
    private int mCurrentRequestIndex = 0;

    /**
     * Returns the resource id for the preference XML of this fragment.
     */
    @XmlRes
    protected abstract int getPreferenceScreenResId();

    /**
     * Returns the layout id to use as the activity action bar. Subclasses should override this
     * method to customize the action bar layout (e.g. additional buttons, switches, etc.). The
     * default action bar contains a back button and the title.
     */
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar;
    }

    /**
     * Returns the controller of the given {@code clazz} for the given {@code
     * preferenceKeyResId}. Subclasses may use this method in {@link #onAttach(Context)} to call
     * setters on controllers to pass additional arguments after construction.
     *
     * <p>For example:
     * <pre>{@code
     * @Override
     * public void onAttach(Context context) {
     *     super.onAttach(context);
     *     use(MyPreferenceController.class, R.string.pk_my_key).setMyArg(myArg);
     * }
     * }</pre>
     *
     * <p>Important: Use judiciously to minimize tight coupling between controllers and fragments.
     */
    @SuppressWarnings("unchecked") // Class is used as map key.
    protected <T extends PreferenceController> T use(Class<T> clazz,
            @StringRes int preferenceKeyResId) {
        List<PreferenceController> controllerList = mPreferenceControllersLookup.get(clazz);
        if (controllerList != null) {
            String preferenceKey = getString(preferenceKeyResId);
            for (PreferenceController controller : controllerList) {
                if (controller.getPreferenceKey().equals(preferenceKey)) {
                    return (T) controller;
                }
            }
        }
        return null;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(getActivity() instanceof UxRestrictionsProvider)) {
            throw new IllegalStateException("Must attach to a UxRestrictionsProvider");
        }
        if (!(getActivity() instanceof FragmentController)) {
            throw new IllegalStateException("Must attach to a FragmentController");
        }

        TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(androidx.preference.R.attr.preferenceTheme, tv,
                true);
        int theme = tv.resourceId;
        if (theme == 0) {
            throw new IllegalStateException("Must specify preferenceTheme in theme");
        }
        // Construct a context with the theme as controllers may create new preferences.
        Context styledContext = new ContextThemeWrapper(getActivity(), theme);

        mUxRestrictions = ((UxRestrictionsProvider) requireActivity()).getCarUxRestrictions();
        mPreferenceControllers.clear();
        mPreferenceControllers.addAll(
                PreferenceControllerListHelper.getPreferenceControllersFromXml(styledContext,
                        getPreferenceScreenResId(), /* fragmentController= */ this,
                        mUxRestrictions));

        Lifecycle lifecycle = getLifecycle();
        mPreferenceControllers.forEach(controller -> {
            lifecycle.addObserver(controller);
            mPreferenceControllersLookup.computeIfAbsent(controller.getClass(),
                    k -> new ArrayList<>(/* initialCapacity= */ 1)).add(controller);
        });
    }

    /**
     * Inflates the preferences from {@link #getPreferenceScreenResId()} and associates the
     * preference with their corresponding {@link PreferenceController} instances.
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        @XmlRes int resId = getPreferenceScreenResId();
        if (resId <= 0) {
            throw new IllegalStateException(
                    "Fragment must specify a preference screen resource ID");
        }
        addPreferencesFromResource(resId);
        PreferenceScreen screen = getPreferenceScreen();
        for (PreferenceController controller : mPreferenceControllers) {
            controller.setPreference(screen.findPreference(controller.getPreferenceKey()));
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        FrameLayout actionBarContainer = requireActivity().findViewById(R.id.action_bar);
        if (actionBarContainer != null) {
            actionBarContainer.removeAllViews();
            getLayoutInflater().inflate(getActionBarLayoutId(), actionBarContainer);

            TextView titleView = actionBarContainer.requireViewById(R.id.title);
            titleView.setText(getPreferenceScreen().getTitle());

            // If the fragment is root, change the back button to settings icon.
            ImageView imageView = actionBarContainer.requireViewById(R.id.back_button);
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            if (fragmentManager.getBackStackEntryCount() == 1
                    && fragmentManager.findFragmentByTag("0") != null
                    && fragmentManager.findFragmentByTag("0").getClass().getName().equals(
                    getString(R.string.config_settings_hierarchy_root_fragment))) {
                if (getContext().getResources()
                        .getBoolean(R.bool.config_show_settings_root_exit_icon)) {
                    imageView.setImageResource(R.drawable.ic_launcher_settings);
                    imageView.setTag(R.id.back_button, R.drawable.ic_launcher_settings);
                } else {
                    hideExitIcon();
                }
            } else {
                imageView.setTag(R.id.back_button, R.drawable.ic_arrow_back);
                actionBarContainer.requireViewById(R.id.action_bar_icon_container)
                        .setOnClickListener(
                                v -> requireActivity().onBackPressed());
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Lifecycle lifecycle = getLifecycle();
        mPreferenceControllers.forEach(lifecycle::removeObserver);
        mActivityResultCallbackMap.clear();
    }

    /**
     * Notifies {@link PreferenceController} instances of changes to {@link CarUxRestrictions}.
     */
    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions uxRestrictions) {
        if (!uxRestrictions.isSameRestrictions(mUxRestrictions)) {
            mUxRestrictions = uxRestrictions;
            for (PreferenceController controller : mPreferenceControllers) {
                controller.onUxRestrictionsChanged(uxRestrictions);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Settings needs to launch custom dialog types in order to extend the Device Default theme.
     *
     * @param preference The Preference object requesting the dialog.
     */
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (findDialogByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        DialogFragment dialogFragment;
        if (preference instanceof ValidatedEditTextPreference) {
            if (preference instanceof PasswordEditTextPreference) {
                dialogFragment = PasswordEditTextPreferenceDialogFragment.newInstance(
                        preference.getKey());
            } else {
                dialogFragment = ValidatedEditTextPreferenceDialogFragment.newInstance(
                        preference.getKey());
            }
        } else if (preference instanceof EditTextPreference) {
            dialogFragment = EditTextPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ListPreference) {
            dialogFragment = SettingsListPreferenceDialogFragment.newInstance(preference.getKey());
        } else {
            throw new IllegalArgumentException(
                    "Tried to display dialog for unknown preference type. Did you forget to "
                            + "override onDisplayPreferenceDialog()?");
        }

        dialogFragment.setTargetFragment(/* fragment= */ this, /* requestCode= */ 0);
        showDialog(dialogFragment, DIALOG_FRAGMENT_TAG);
    }

    @Override
    public void launchFragment(Fragment fragment) {
        ((FragmentController) requireActivity()).launchFragment(fragment);
    }

    @Override
    public void goBack() {
        requireActivity().onBackPressed();
    }

    @Override
    public void showBlockingMessage() {
        Toast.makeText(getContext(), R.string.restricted_while_driving, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showDialog(DialogFragment dialogFragment, @Nullable String tag) {
        dialogFragment.show(getFragmentManager(), tag);
    }

    @Nullable
    @Override
    public DialogFragment findDialogByTag(String tag) {
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof DialogFragment) {
            return (DialogFragment) fragment;
        }
        return null;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode,
            ActivityResultCallback callback) {
        validateRequestCodeForPreferenceController(requestCode);
        int requestIndex = allocateRequestIndex(callback);
        super.startActivityForResult(intent, ((requestIndex + 1) << 8) + (requestCode & 0xff));
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            @Nullable Intent fillInIntent, int flagsMask, int flagsValues, Bundle options,
            ActivityResultCallback callback)
            throws IntentSender.SendIntentException {
        validateRequestCodeForPreferenceController(requestCode);
        int requestIndex = allocateRequestIndex(callback);
        super.startIntentSenderForResult(intent, ((requestIndex + 1) << 8) + (requestCode & 0xff),
                fillInIntent, flagsMask, flagsValues, /* extraFlags= */ 0, options);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int requestIndex = (requestCode >> 8) & 0xff;
        if (requestIndex != 0) {
            requestIndex--;
            ActivityResultCallback callback = mActivityResultCallbackMap.get(requestIndex);
            mActivityResultCallbackMap.remove(requestIndex);
            if (callback != null) {
                callback.processActivityResult(requestCode & 0xff, resultCode, data);
            }
        }
    }

    // Allocates the next available startActivityForResult request index.
    private int allocateRequestIndex(ActivityResultCallback callback) {
        // Sanity check that we haven't exhausted the request index space.
        if (mActivityResultCallbackMap.size() >= MAX_NUM_PENDING_ACTIVITY_RESULT_CALLBACKS) {
            throw new IllegalStateException(
                    "Too many pending activity result callbacks.");
        }

        // Find an unallocated request index in the mPendingFragmentActivityResults map.
        while (mActivityResultCallbackMap.indexOfKey(mCurrentRequestIndex) >= 0) {
            mCurrentRequestIndex =
                    (mCurrentRequestIndex + 1) % MAX_NUM_PENDING_ACTIVITY_RESULT_CALLBACKS;
        }

        mActivityResultCallbackMap.put(mCurrentRequestIndex, callback);
        return mCurrentRequestIndex;
    }

    /**
     * Checks whether the given request code is a valid code by masking it with 0xff00. Throws an
     * {@link IllegalArgumentException} if the code is not valid.
     */
    private static void validateRequestCodeForPreferenceController(int requestCode) {
        if ((requestCode & 0xff00) != 0) {
            throw new IllegalArgumentException("Can only use lower 8 bits for requestCode");
        }
    }

    private void hideExitIcon() {
        requireActivity().findViewById(R.id.action_bar_icon_container)
                .setVisibility(FrameLayout.GONE);

        Guideline guideLine = (Guideline) requireActivity().findViewById(R.id.start_margin);
        guideLine.setGuidelineBegin(getResources()
                .getDimensionPixelOffset(R.dimen.action_bar_no_icon_start_margin));
    }
}
