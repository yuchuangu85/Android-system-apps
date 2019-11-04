/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.car.cluster;

import static android.car.cluster.ClusterRenderingService.LOCAL_BINDING_ACTION;
import static android.content.Intent.ACTION_USER_SWITCHED;
import static android.content.Intent.ACTION_USER_UNLOCKED;
import static android.content.PermissionChecker.PERMISSION_GRANTED;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cluster.sensors.Sensors;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import com.android.car.telephony.common.InMemoryPhoneBook;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Main activity displayed on the instrument cluster. This activity contains fragments for each of
 * the cluster "facets" (e.g.: navigation, communication, media and car state). Users can navigate
 * to each facet by using the steering wheel buttons.
 * <p>
 * This activity runs on "system user" (see {@link UserHandle#USER_SYSTEM}) but it is visible on
 * all users (the same activity remains active even during user switch).
 * <p>
 * This activity also launches a default navigation app inside a virtual display (which is located
 * inside {@link NavigationFragment}). This navigation app is launched when:
 * <ul>
 * <li>Virtual display for navigation apps is ready.
 * <li>After every user switch.
 * </ul>
 * This is necessary because the navigation app runs under a normal user, and different users will
 * see different instances of the same application, with their own personalized data.
 */
public class MainClusterActivity extends FragmentActivity implements
        ClusterRenderingService.ServiceClient {
    private static final String TAG = "Cluster.MainActivity";

    private static final int NAV_FACET_ID = 0;
    private static final int COMMS_FACET_ID = 1;
    private static final int MEDIA_FACET_ID = 2;
    private static final int INFO_FACET_ID = 3;

    private static final NavigationStateProto NULL_NAV_STATE =
            NavigationStateProto.getDefaultInstance();
    private static final int NO_DISPLAY = -1;

    private ViewPager mPager;
    private NavStateController mNavStateController;
    private ClusterViewModel mClusterViewModel;

    private Map<View, Facet<?>> mButtonToFacet = new HashMap<>();
    private SparseArray<Facet<?>> mOrderToFacet = new SparseArray<>();

    private Map<Sensors.Gear, View> mGearsToIcon = new HashMap<>();
    private InputMethodManager mInputMethodManager;
    private ClusterRenderingService mService;
    private VirtualDisplay mPendingVirtualDisplay = null;

    private static final int NAVIGATION_ACTIVITY_RETRY_INTERVAL_MS = 1000;
    private static final int NAVIGATION_ACTIVITY_RELAUNCH_DELAY_MS = 5000;

    private UserReceiver mUserReceiver;
    private ActivityMonitor mActivityMonitor = new ActivityMonitor();
    private final Handler mHandler = new Handler();
    private final Runnable mRetryLaunchNavigationActivity = this::tryLaunchNavigationActivity;
    private VirtualDisplay mNavigationDisplay = new VirtualDisplay(NO_DISPLAY, null);

    private int mPreviousFacet = COMMS_FACET_ID;

    /**
     * Description of a virtual display
     */
    public static class VirtualDisplay {
        /** Identifier of the display */
        public final int mDisplayId;
        /** Rectangular area inside this display that can be viewed without obstructions */
        public final Rect mUnobscuredBounds;

        public VirtualDisplay(int displayId, Rect unobscuredBounds) {
            mDisplayId = displayId;
            mUnobscuredBounds = unobscuredBounds;
        }
    }

    private final View.OnFocusChangeListener mFacetButtonFocusListener =
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        mPager.setCurrentItem(mButtonToFacet.get(v).mOrder);
                    }
                }
            };

    private ServiceConnection mClusterRenderingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected, name: " + name + ", service: " + service);
            mService = ((ClusterRenderingService.LocalBinder) service).getService();
            mService.registerClient(MainClusterActivity.this);
            mNavStateController.setImageResolver(mService.getImageResolver());
            if (mPendingVirtualDisplay != null) {
                // If haven't reported the virtual display yet, do so on service connect.
                reportNavDisplay(mPendingVirtualDisplay);
                mPendingVirtualDisplay = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected, name: " + name);
            mService = null;
            mNavStateController.setImageResolver(null);
            onNavigationStateChange(NULL_NAV_STATE);
        }
    };

    private ActivityMonitor.ActivityListener mNavigationActivityMonitor = (displayId, activity) -> {
        if (displayId != mNavigationDisplay.mDisplayId) {
            return;
        }
        mClusterViewModel.setCurrentNavigationActivity(activity);
    };

    private static class UserReceiver extends BroadcastReceiver {
        private WeakReference<MainClusterActivity> mActivity;

        UserReceiver(MainClusterActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public void register(Context context) {
            IntentFilter intentFilter = new IntentFilter(ACTION_USER_UNLOCKED);
            intentFilter.addAction(ACTION_USER_SWITCHED);
            context.registerReceiver(this, intentFilter);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MainClusterActivity activity = mActivity.get();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Broadcast received: " + intent);
            }
            activity.tryLaunchNavigationActivity();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        mInputMethodManager = getSystemService(InputMethodManager.class);

        Intent intent = new Intent(this, ClusterRenderingService.class);
        intent.setAction(LOCAL_BINDING_ACTION);
        bindServiceAsUser(intent, mClusterRenderingServiceConnection, 0, UserHandle.SYSTEM);

        registerFacet(new Facet<>(findViewById(R.id.btn_nav),
                NAV_FACET_ID, NavigationFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_phone),
                COMMS_FACET_ID, PhoneFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_music),
                MEDIA_FACET_ID, MusicFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_car_info),
                INFO_FACET_ID, CarInfoFragment.class));
        registerGear(findViewById(R.id.gear_parked), Sensors.Gear.PARK);
        registerGear(findViewById(R.id.gear_reverse), Sensors.Gear.REVERSE);
        registerGear(findViewById(R.id.gear_neutral), Sensors.Gear.NEUTRAL);
        registerGear(findViewById(R.id.gear_drive), Sensors.Gear.DRIVE);

        mPager = findViewById(R.id.pager);
        mPager.setAdapter(new ClusterPageAdapter(getSupportFragmentManager()));
        mOrderToFacet.get(NAV_FACET_ID).mButton.requestFocus();
        mNavStateController = new NavStateController(findViewById(R.id.navigation_state));

        mClusterViewModel = ViewModelProviders.of(this).get(ClusterViewModel.class);
        mClusterViewModel.getNavigationFocus().observe(this, focus -> {
            // If focus is lost, we launch the default navigation activity again.
            if (!focus) {
                mNavStateController.update(null);
                tryLaunchNavigationActivity();
            }
        });
        mClusterViewModel.getNavigationActivityState().observe(this, state -> {
            if (state == ClusterViewModel.NavigationActivityState.LOADING) {
                if (!mHandler.hasCallbacks(mRetryLaunchNavigationActivity)) {
                    mHandler.postDelayed(mRetryLaunchNavigationActivity,
                            NAVIGATION_ACTIVITY_RELAUNCH_DELAY_MS);
                }
            } else {
                mHandler.removeCallbacks(mRetryLaunchNavigationActivity);
            }
        });

        mClusterViewModel.getSensor(Sensors.SENSOR_GEAR).observe(this, this::updateSelectedGear);

        registerSensor(findViewById(R.id.info_fuel), mClusterViewModel.getFuelLevel());
        registerSensor(findViewById(R.id.info_speed), mClusterViewModel.getSpeed());
        registerSensor(findViewById(R.id.info_range), mClusterViewModel.getRange());
        registerSensor(findViewById(R.id.info_rpm), mClusterViewModel.getRPM());

        mActivityMonitor.start();

        mUserReceiver = new UserReceiver(this);
        mUserReceiver.register(this);

        InMemoryPhoneBook.init(this);

        PhoneFragmentViewModel phoneViewModel = ViewModelProviders.of(this).get(
                PhoneFragmentViewModel.class);

        phoneViewModel.setPhoneStateCallback(new PhoneFragmentViewModel.PhoneStateCallback() {
            @Override
            public void onCall() {
                if (mPager.getCurrentItem() != COMMS_FACET_ID) {
                    mPreviousFacet = mPager.getCurrentItem();
                }
                mOrderToFacet.get(COMMS_FACET_ID).mButton.requestFocus();
            }

            @Override
            public void onDisconnect() {
                if (mPreviousFacet != COMMS_FACET_ID) {
                    mOrderToFacet.get(mPreviousFacet).mButton.requestFocus();
                }
            }
        });
    }

    private <V> void registerSensor(TextView textView, LiveData<V> source) {
        String emptyValue = getString(R.string.info_value_empty);
        source.observe(this, value -> textView.setText(value != null
                ? value.toString() : emptyValue));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mUserReceiver.unregister(this);
        mActivityMonitor.stop();
        if (mService != null) {
            mService.unregisterClient(this);
            mService = null;
        }
        unbindService(mClusterRenderingServiceConnection);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        Log.i(TAG, "onKeyEvent, event: " + event);

        // This is a hack. We use SOURCE_CLASS_POINTER here because this type of input is associated
        // with the display. otherwise this event will be ignored in ViewRootImpl because injecting
        // KeyEvent w/o activity being focused is useless.
        event.setSource(event.getSource() | InputDevice.SOURCE_CLASS_POINTER);
        mInputMethodManager.dispatchKeyEventFromInputMethod(getCurrentFocus(), event);
    }

    @Override
    public void onNavigationStateChange(NavigationStateProto state) {
        Log.d(TAG, "onNavigationStateChange: " + state);
        if (mNavStateController != null) {
            mNavStateController.update(state);
        }
    }

    public void updateNavDisplay(VirtualDisplay virtualDisplay) {
        // Starting the default navigation activity. This activity will be shown when navigation
        // focus is not taken.
        startNavigationActivity(virtualDisplay);
        // Notify the service (so it updates display properties on car service)
        if (mService == null) {
            // Service is not bound yet. Hold the information and notify when the service is bound.
            mPendingVirtualDisplay = virtualDisplay;
            return;
        } else {
            reportNavDisplay(virtualDisplay);
        }
    }

    private void reportNavDisplay(VirtualDisplay virtualDisplay) {
        mService.setActivityLaunchOptions(virtualDisplay.mDisplayId, ClusterActivityState
                .create(virtualDisplay.mDisplayId != Display.INVALID_DISPLAY,
                        virtualDisplay.mUnobscuredBounds));
    }

    public class ClusterPageAdapter extends FragmentPagerAdapter {
        public ClusterPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return mButtonToFacet.size();
        }

        @Override
        public Fragment getItem(int position) {
            return mOrderToFacet.get(position).getOrCreateFragment();
        }
    }

    private <T> void registerFacet(Facet<T> facet) {
        mOrderToFacet.append(facet.mOrder, facet);
        mButtonToFacet.put(facet.mButton, facet);

        facet.mButton.setOnFocusChangeListener(mFacetButtonFocusListener);
    }

    private static class Facet<T> {
        Button mButton;
        Class<T> mClazz;
        int mOrder;

        Facet(Button button, int order, Class<T> clazz) {
            this.mButton = button;
            this.mOrder = order;
            this.mClazz = clazz;
        }

        private Fragment mFragment;

        Fragment getOrCreateFragment() {
            if (mFragment == null) {
                try {
                    mFragment = (Fragment) mClazz.getConstructors()[0].newInstance();
                } catch (InstantiationException | IllegalAccessException
                        | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return mFragment;
        }
    }

    private void startNavigationActivity(VirtualDisplay virtualDisplay) {
        mActivityMonitor.removeListener(mNavigationDisplay.mDisplayId, mNavigationActivityMonitor);
        mActivityMonitor.addListener(virtualDisplay.mDisplayId, mNavigationActivityMonitor);
        mNavigationDisplay = virtualDisplay;
        tryLaunchNavigationActivity();
    }

    /**
     * Tries to start a default navigation activity in the cluster. During system initialization
     * launching user activities might fail due the system not being ready or {@link PackageManager}
     * not being able to resolve the implicit intent. It is also possible that the system doesn't
     * have a default navigation activity selected yet.
     */
    private void tryLaunchNavigationActivity() {
        if (mNavigationDisplay.mDisplayId == NO_DISPLAY) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Launch activity ignored (no display yet)"));
            }
            // Not ready to launch yet.
            return;
        }
        mHandler.removeCallbacks(mRetryLaunchNavigationActivity);

        ComponentName navigationActivity = getNavigationActivity();
        mClusterViewModel.setFreeNavigationActivity(navigationActivity);

        try {
            if (navigationActivity == null) {
                throw new ActivityNotFoundException();
            }
            ClusterActivityState activityState = ClusterActivityState
                    .create(true, mNavigationDisplay.mUnobscuredBounds);
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(navigationActivity)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(CarInstrumentClusterManager.KEY_EXTRA_ACTIVITY_STATE,
                            activityState.toBundle());

            Log.d(TAG, "Launching: " + intent + " on display: " + mNavigationDisplay.mDisplayId);
            Bundle activityOptions = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(mNavigationDisplay.mDisplayId)
                    .toBundle();

            startActivityAsUser(intent, activityOptions, UserHandle.CURRENT);
        } catch (ActivityNotFoundException ex) {
            // Some activities might not be available right on startup. We will retry.
            mHandler.postDelayed(mRetryLaunchNavigationActivity,
                    NAVIGATION_ACTIVITY_RETRY_INTERVAL_MS);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to start navigation activity: " + navigationActivity, ex);
        }
    }

    /**
     * Returns a default navigation activity to show in the cluster.
     * In the current implementation we obtain this activity from an intent defined in a resources
     * file (which OEMs can overlay).
     * Alternatively, other implementations could:
     * <ul>
     * <li>Dynamically detect what's the default navigation activity the user has selected on the
     * head unit, and obtain the activity marked with
     * {@link CarInstrumentClusterManager#CATEGORY_NAVIGATION} from the same package.
     * <li>Let the user select one from settings.
     * </ul>
     */
    private ComponentName getNavigationActivity() {
        PackageManager pm = getPackageManager();
        int userId = ActivityManager.getCurrentUser();
        String intentString = getString(R.string.freeNavigationIntent);

        if (intentString == null) {
            Log.w(TAG, "No free navigation activity defined");
            return null;
        }
        Log.i(TAG, "Free navigation intent: " + intentString);

        try {
            Intent intent = Intent.parseUri(intentString, Intent.URI_INTENT_SCHEME);
            ResolveInfo navigationApp = pm.resolveActivityAsUser(intent,
                    PackageManager.MATCH_DEFAULT_ONLY, userId);
            if (navigationApp == null) {
                return null;
            }

            // Check that it has the right permissions
            if (pm.checkPermission(Car.PERMISSION_CAR_DISPLAY_IN_CLUSTER, navigationApp.activityInfo
                    .packageName) != PERMISSION_GRANTED) {
                Log.i(TAG, String.format("Package '%s' doesn't have permission %s",
                        navigationApp.activityInfo.packageName,
                        Car.PERMISSION_CAR_DISPLAY_IN_CLUSTER));
                return null;
            }

            return new ComponentName(navigationApp.activityInfo.packageName,
                    navigationApp.activityInfo.name);
        } catch (URISyntaxException ex) {
            Log.e(TAG, "Unable to parse free navigation activity intent: '" + intentString + "'");
            return null;
        }
    }

    private void registerGear(View view, Sensors.Gear gear) {
        mGearsToIcon.put(gear, view);
    }

    private void updateSelectedGear(Sensors.Gear gear) {
        for (Map.Entry<Sensors.Gear, View> entry : mGearsToIcon.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == gear);
        }
    }
}
