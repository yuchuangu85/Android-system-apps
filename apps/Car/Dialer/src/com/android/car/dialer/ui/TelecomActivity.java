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

package com.android.car.dialer.ui;

import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.android.car.apps.common.util.Themes;
import com.android.car.apps.common.widget.CarTabLayout;
import com.android.car.dialer.Constants;
import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.notification.NotificationService;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.ui.activecall.InCallActivity;
import com.android.car.dialer.ui.activecall.InCallViewModel;
import com.android.car.dialer.ui.calllog.CallHistoryFragment;
import com.android.car.dialer.ui.common.DialerBaseFragment;
import com.android.car.dialer.ui.contact.ContactListFragment;
import com.android.car.dialer.ui.dialpad.DialpadFragment;
import com.android.car.dialer.ui.favorite.FavoriteFragment;
import com.android.car.dialer.ui.search.ContactResultsFragment;
import com.android.car.dialer.ui.settings.DialerSettingsActivity;
import com.android.car.dialer.ui.warning.NoHfpFragment;

import java.util.List;

/**
 * Main activity for the Dialer app. It contains two layers:
 * <ul>
 * <li>Overlay layer for {@link NoHfpFragment}
 * <li>Content layer for {@link FavoriteFragment} {@link CallHistoryFragment} {@link
 * ContactListFragment} and {@link DialpadFragment}
 *
 * <p>Start {@link InCallActivity} if there are ongoing calls
 *
 * <p>Based on call and connectivity status, it will choose the right page to display.
 */
public class TelecomActivity extends FragmentActivity implements
        DialerBaseFragment.DialerFragmentParent, FragmentManager.OnBackStackChangedListener {
    private static final String TAG = "CD.TelecomActivity";

    private LiveData<String> mBluetoothErrorMsgLiveData;
    private LiveData<Integer> mDialerAppStateLiveData;
    private LiveData<List<Call>> mOngoingCallListLiveData;

    // View objects for this activity.
    private CarTabLayout<TelecomPageTab> mTabLayout;
    private TelecomPageTab.Factory mTabFactory;
    private Toolbar mToolbar;
    private View mToolbarContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        L.d(TAG, "onCreate");
        setContentView(R.layout.telecom_activity);

        mToolbar = findViewById(R.id.car_toolbar);
        setActionBar(mToolbar);
        getActionBar().setLogo(R.drawable.sized_logo);

        mToolbarContainer = findViewById(R.id.car_toolbar_container);

        setupTabLayout();

        TelecomActivityViewModel viewModel = ViewModelProviders.of(this).get(
                TelecomActivityViewModel.class);
        mBluetoothErrorMsgLiveData = viewModel.getErrorMessage();
        mDialerAppStateLiveData = viewModel.getDialerAppState();
        mDialerAppStateLiveData.observe(this,
                dialerAppState -> updateCurrentFragment(dialerAppState));

        InCallViewModel inCallViewModel = ViewModelProviders.of(this).get(InCallViewModel.class);
        mOngoingCallListLiveData = inCallViewModel.getOngoingCallList();
        // The mOngoingCallListLiveData needs to be active to get calculated.
        mOngoingCallListLiveData.observe(this, this::maybeStartInCallActivity);

        handleIntent();
    }

    @Override
    public void onStart() {
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        onBackStackChanged();
        super.onStart();
        L.d(TAG, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        L.d(TAG, "onStop");
        getSupportFragmentManager().removeOnBackStackChangedListener(this);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent();
    }

    @Override
    public void setBackground(Drawable background) {
        findViewById(android.R.id.content).setBackground(background);
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        L.d(TAG, "handleIntent, intent: %s, action: %s", intent, action);
        if (action == null || action.length() == 0) {
            return;
        }

        String number;
        switch (action) {
            case Intent.ACTION_DIAL:
                number = PhoneNumberUtils.getNumberFromIntent(intent, this);
                if (TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR
                        != mDialerAppStateLiveData.getValue()) {
                    showDialPadFragment(number);
                }
                break;

            case Intent.ACTION_CALL:
                number = PhoneNumberUtils.getNumberFromIntent(intent, this);
                UiCallManager.get().placeCall(number);
                break;

            case Intent.ACTION_SEARCH:
                String searchQuery = intent.getStringExtra(SearchManager.QUERY);
                navigateToContactResultsFragment(searchQuery);
                break;

            case Constants.Intents.ACTION_SHOW_PAGE:
                if (TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR
                        != mDialerAppStateLiveData.getValue()) {
                    showTabPage(intent.getStringExtra(Constants.Intents.EXTRA_SHOW_PAGE));
                    if (intent.getBooleanExtra(Constants.Intents.EXTRA_ACTION_READ_MISSED, false)) {
                        NotificationService.readAllMissedCall(this);
                    }
                }
                break;

            default:
                // Do nothing.
        }

        setIntent(null);

        // This is to start the incall activity when user taps on the dialer launch icon rapidly
        maybeStartInCallActivity(mOngoingCallListLiveData.getValue());
    }

    /**
     * Update the current visible fragment of this Activity based on the state of the application.
     * <ul>
     * <li> If bluetooth is not connected or there is an active call, show overlay, lock drawer,
     * hide action bar and hide the content layer.
     * <li> Otherwise, show the content layer, show action bar, hide the overlay and reset drawer
     * lock mode.
     */
    private void updateCurrentFragment(
            @TelecomActivityViewModel.DialerAppState int dialerAppState) {
        L.d(TAG, "updateCurrentFragment, dialerAppState: %d", dialerAppState);

        boolean isOverlayFragmentVisible =
                TelecomActivityViewModel.DialerAppState.DEFAULT != dialerAppState;
        findViewById(R.id.content_container)
                .setVisibility(isOverlayFragmentVisible ? View.GONE : View.VISIBLE);
        findViewById(R.id.overlay_container)
                .setVisibility(isOverlayFragmentVisible ? View.VISIBLE : View.GONE);

        switch (dialerAppState) {
            case TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR:
                showNoHfpOverlay(mBluetoothErrorMsgLiveData.getValue());
                break;

            case TelecomActivityViewModel.DialerAppState.EMERGENCY_DIALPAD:
                setOverlayFragment(DialpadFragment.newEmergencyDialpad());
                break;

            case TelecomActivityViewModel.DialerAppState.DEFAULT:
            default:
                clearOverlayFragment();
                break;
        }
    }

    private void showNoHfpOverlay(String errorMsg) {
        Fragment overlayFragment = getCurrentOverlayFragment();
        if (overlayFragment instanceof NoHfpFragment) {
            ((NoHfpFragment) overlayFragment).setErrorMessage(errorMsg);
        } else {
            setOverlayFragment(NoHfpFragment.newInstance(errorMsg));
        }
    }

    private void setOverlayFragment(@NonNull Fragment overlayFragment) {
        L.d(TAG, "setOverlayFragment: %s", overlayFragment);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.overlay_container, overlayFragment)
                .commitNow();
    }

    private void clearOverlayFragment() {
        L.d(TAG, "clearOverlayFragment");

        Fragment overlayFragment = getCurrentOverlayFragment();
        if (overlayFragment == null) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .remove(overlayFragment)
                .commitNow();
    }

    /** Returns the fragment that is currently being displayed as the overlay view on top. */
    @Nullable
    private Fragment getCurrentOverlayFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.overlay_container);
    }

    private void setupTabLayout() {
        mTabLayout = findViewById(R.id.tab_layout);

        boolean hasContentFragment = false;

        mTabFactory = new TelecomPageTab.Factory(this, getSupportFragmentManager());
        for (int i = 0; i < mTabFactory.getTabCount(); i++) {
            TelecomPageTab telecomPageTab = mTabFactory.createTab(getBaseContext(), i);
            mTabLayout.addCarTab(telecomPageTab);

            if (telecomPageTab.wasFragmentRestored()) {
                mTabLayout.selectCarTab(i);
                hasContentFragment = true;
            }
        }

        // Select the starting tab and set up the fragment for it.
        if (!hasContentFragment) {
            int startTabIndex = getTabFromSharedPreference();
            TelecomPageTab startTab = mTabLayout.get(startTabIndex);
            mTabLayout.selectCarTab(startTabIndex);
            setContentFragment(startTab.getFragment(), startTab.getFragmentTag());
        }

        mTabLayout.addOnCarTabSelectedListener(
                new CarTabLayout.SimpleOnCarTabSelectedListener<TelecomPageTab>() {
                    @Override
                    public void onCarTabSelected(TelecomPageTab telecomPageTab) {
                        Fragment fragment = telecomPageTab.getFragment();
                        setContentFragment(fragment, telecomPageTab.getFragmentTag());
                    }
                });
    }

    /** Switch to {@link DialpadFragment} and set the given number as dialed number. */
    private void showDialPadFragment(String number) {
        int dialpadTabIndex = showTabPage(TelecomPageTab.Page.DIAL_PAD);

        if (dialpadTabIndex == -1) {
            return;
        }

        TelecomPageTab dialpadTab = mTabLayout.get(dialpadTabIndex);
        Fragment fragment = dialpadTab.getFragment();
        if (fragment instanceof DialpadFragment) {
            ((DialpadFragment) fragment).setDialedNumber(number);
        } else {
            L.w(TAG, "Current tab is not a dialpad fragment!");
        }
    }

    private int showTabPage(@TelecomPageTab.Page String tabPage) {
        int tabIndex = mTabFactory.getTabIndex(tabPage);
        if (tabIndex == -1) {
            L.w(TAG, "Page %s is not a tab.", tabPage);
            return -1;
        }
        getSupportFragmentManager().executePendingTransactions();
        while (isBackNavigationAvailable()) {
            getSupportFragmentManager().popBackStackImmediate();
        }

        mTabLayout.selectCarTab(tabIndex);
        return tabIndex;
    }

    private void setContentFragment(Fragment fragment, String fragmentTag) {
        L.d(TAG, "setContentFragment: %s", fragment);

        getSupportFragmentManager().executePendingTransactions();
        while (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_fragment_container, fragment, fragmentTag)
                .addToBackStack(fragmentTag)
                .commit();
    }

    @Override
    public void pushContentFragment(@NonNull Fragment topContentFragment, String fragmentTag) {
        L.d(TAG, "pushContentFragment: %s", topContentFragment);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_fragment_container, topContentFragment)
                .addToBackStack(fragmentTag)
                .commit();
    }

    @Override
    public void onBackStackChanged() {
        boolean isBackNavigationAvailable = isBackNavigationAvailable();
        mTabLayout.setVisibility(isBackNavigationAvailable ? View.GONE : View.VISIBLE);
        int displayOptions = Themes.getAttrInteger(
                this,
                isBackNavigationAvailable ? R.style.HomeAsUpDisplayOptions
                        : R.style.RootToolbarDisplayOptions,
                android.R.attr.displayOptions);
        getActionBar().setDisplayOptions(displayOptions);

        Fragment topFragment = getSupportFragmentManager().findFragmentById(
                R.id.content_fragment_container);
        if (topFragment instanceof DialerBaseFragment) {
            ((DialerBaseFragment) topFragment).setupActionBar(getActionBar());
        }
    }

    @Override
    public boolean onNavigateUp() {
        if (isBackNavigationAvailable()) {
            onBackPressed();
            return true;
        }
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        // By default onBackPressed will pop all the fragments off the backstack and then finish
        // the activity. We want to finish the activity while there is still one fragment on the
        // backstack, because we use onBackStackChanged() to set up our fragments.
        if (isBackNavigationAvailable()) {
            super.onBackPressed();
        } else {
            finishAfterTransition();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        MenuItem searchMenu = menu.findItem(R.id.menu_contacts_search);
        Intent searchIntent = new Intent(getApplicationContext(), TelecomActivity.class);
        searchIntent.setAction(Intent.ACTION_SEARCH);
        searchMenu.setIntent(searchIntent);

        MenuItem settingsMenu = menu.findItem(R.id.menu_dialer_setting);
        Intent settingsIntent = new Intent(getApplicationContext(), DialerSettingsActivity.class);
        settingsMenu.setIntent(settingsIntent);
        return true;
    }

    private void navigateToContactResultsFragment(String query) {
        Fragment topFragment = getSupportFragmentManager().findFragmentById(
                R.id.content_fragment_container);

        // Top fragment is ContactResultsFragment, update search query
        if (topFragment instanceof ContactResultsFragment) {
            ((ContactResultsFragment) topFragment).setSearchQuery(query);
            return;
        }

        ContactResultsFragment fragment = ContactResultsFragment.newInstance(query);
        pushContentFragment(fragment, ContactResultsFragment.FRAGMENT_TAG);
    }

    private void maybeStartInCallActivity(List<Call> callList) {
        if (callList == null || callList.isEmpty()) {
            return;
        }

        L.d(TAG, "Start InCallActivity");
        Intent launchIntent = new Intent(getApplicationContext(), InCallActivity.class);
        startActivity(launchIntent);
    }

    /** If the back button on action bar is available to navigate up. */
    private boolean isBackNavigationAvailable() {
        return getSupportFragmentManager().getBackStackEntryCount() > 1;
    }

    private int getTabFromSharedPreference() {
        String key = getResources().getString(R.string.pref_start_page_key);
        String defaultValue = getResources().getStringArray(R.array.tabs_config)[0];
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return mTabFactory.getTabIndex(sharedPreferences.getString(key, defaultValue));
    }

    /** Sets the background of the Activity's action bar to a {@link Drawable} */
    public void setActionBarBackground(@Nullable Drawable drawable) {
        if (mToolbarContainer != null) {
            mToolbarContainer.setBackground(drawable);
        } else {
            mToolbar.setBackground(drawable);
        }
    }
}
