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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static java.lang.Integer.parseInt;

import android.app.ActivityOptions;
import android.car.CarNotConnectedException;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.car.cluster.renderer.NavigationRenderer;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.versionedparcelable.ParcelUtils;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of {@link InstrumentClusterRenderingService} which renders an activity on a
 * virtual display that is transmitted to an external screen.
 */
public class ClusterRenderingService extends InstrumentClusterRenderingService implements
        ImageResolver.BitmapFetcher {
    private static final String TAG = "Cluster.Service";

    private static final int NO_DISPLAY = -1;

    static final int NAV_STATE_EVENT_ID = 1;
    static final String LOCAL_BINDING_ACTION = "local";
    static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";

    private List<ServiceClient> mClients = new ArrayList<>();
    private ClusterDisplayProvider mDisplayProvider;
    private int mDisplayId = NO_DISPLAY;
    private final IBinder mLocalBinder = new LocalBinder();
    private final ImageResolver mImageResolver = new ImageResolver(this);

    public interface ServiceClient {
        void onKeyEvent(KeyEvent keyEvent);

        void onNavigationStateChange(NavigationStateProto navState);
    }

    public class LocalBinder extends Binder {
        ClusterRenderingService getService() {
            return ClusterRenderingService.this;
        }
    }

    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            Log.i(TAG, "Cluster display found, displayId: " + displayId);
            mDisplayId = displayId;
            launchMainActivity();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            Log.w(TAG, "Cluster display has been removed");
        }

        @Override
        public void onDisplayChanged(int displayId) {

        }
    };

    public void setActivityLaunchOptions(int displayId, ClusterActivityState state) {
        try {
            ActivityOptions options = displayId != Display.INVALID_DISPLAY
                    ? ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                    : null;
            setClusterActivityLaunchOptions(CarInstrumentClusterManager.CATEGORY_NAVIGATION,
                    options);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("activity options set: %s (displayeId: %d)",
                        options, options.getLaunchDisplayId()));
            }
            setClusterActivityState(CarInstrumentClusterManager.CATEGORY_NAVIGATION,
                    state.toBundle());
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("activity state set: %s", state));
            }
        } catch (CarNotConnectedException ex) {
            Log.e(TAG, "Unable to update service", ex);
        }
    }

    public void registerClient(ServiceClient client) {
        mClients.add(client);
    }

    public void unregisterClient(ServiceClient client) {
        mClients.remove(client);
    }

    public ImageResolver getImageResolver() {
        return mImageResolver;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind, intent: " + intent);
        return LOCAL_BINDING_ACTION.equals(intent.getAction())
                ? mLocalBinder
                : super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mDisplayProvider = new ClusterDisplayProvider(this, mDisplayListener);
    }

    private void launchMainActivity() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(mDisplayId);
        Intent intent = new Intent(this, MainClusterActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivityAsUser(intent, options.toBundle(), UserHandle.SYSTEM);
        Log.i(TAG, String.format("launching main activity: %s (display: %d)", intent, mDisplayId));
    }

    @Override
    public void onKeyEvent(KeyEvent keyEvent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onKeyEvent, keyEvent: " + keyEvent);
        }
        broadcastClientEvent(client -> client.onKeyEvent(keyEvent));
    }

    /**
     * Broadcasts an event to all the registered service clients
     *
     * @param event event to broadcast
     */
    private void broadcastClientEvent(Consumer<ServiceClient> event) {
        for (ServiceClient client : mClients) {
            event.accept(client);
        }
    }

    @Override
    public NavigationRenderer getNavigationRenderer() {
        NavigationRenderer navigationRenderer = new NavigationRenderer() {
            @Override
            public CarNavigationInstrumentCluster getNavigationProperties() {
                CarNavigationInstrumentCluster config =
                        CarNavigationInstrumentCluster.createCluster(1000);
                Log.d(TAG, "getNavigationProperties, returns: " + config);
                return config;
            }

            @Override
            public void onEvent(int eventType, Bundle bundle) {
                StringBuilder bundleSummary = new StringBuilder();
                if (eventType == NAV_STATE_EVENT_ID) {
                    // Required to prevent backwards compatibility crash with old map providers
                    // sending androidx.versionedparcelables
                    bundle.setClassLoader(ParcelUtils.class.getClassLoader());
                    
                    // Attempt to read proto byte array
                    byte[] protoBytes = bundle.getByteArray(NAV_STATE_PROTO_BUNDLE_KEY);
                    if (protoBytes != null) {
                        try {
                            NavigationStateProto navState = NavigationStateProto.parseFrom(
                                    protoBytes);
                            bundleSummary.append(navState.toString());

                            // Update clients
                            broadcastClientEvent(
                                    client -> client.onNavigationStateChange(navState));
                        } catch (InvalidProtocolBufferException e) {
                            Log.e(TAG, "Error parsing navigation state proto", e);
                        }
                    } else {
                        Log.e(TAG, "Received nav state byte array is null");
                    }
                } else {
                    for (String key : bundle.keySet()) {
                        bundleSummary.append(key);
                        bundleSummary.append("=");
                        bundleSummary.append(bundle.get(key));
                        bundleSummary.append(" ");
                    }
                }
                Log.d(TAG, "onEvent(" + eventType + ", " + bundleSummary + ")");
            }
        };

        Log.i(TAG, "createNavigationRenderer, returns: " + navigationRenderer);
        return navigationRenderer;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length > 0) {
            execShellCommand(args);
        } else {
            super.dump(fd, writer, args);
            writer.println("DisplayProvider: " + mDisplayProvider);
        }
    }

    private void emulateKeyEvent(int keyCode) {
        Log.i(TAG, "emulateKeyEvent, keyCode: " + keyCode);
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_DOWN);
        onKeyEvent(event);

        eventTime = SystemClock.uptimeMillis();
        event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_UP);
        onKeyEvent(event);
    }

    private KeyEvent obtainKeyEvent(int keyCode, long downTime, long eventTime, int action) {
        int scanCode = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scanCode = 108;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            scanCode = 106;
        }
        return KeyEvent.obtain(
                downTime,
                eventTime,
                action,
                keyCode,
                0 /* repeat */,
                0 /* meta state */,
                0 /* deviceId*/,
                scanCode /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM /* flags */,
                InputDevice.SOURCE_KEYBOARD,
                null /* characters */);
    }

    private void execShellCommand(String[] args) {
        Log.i(TAG, "execShellCommand, args: " + Arrays.toString(args));

        String command = args[0];

        switch (command) {
            case "injectKey": {
                if (args.length > 1) {
                    emulateKeyEvent(parseInt(args[1]));
                } else {
                    Log.i(TAG, "Not enough arguments");
                }
                break;
            }
            case "destroyOverlayDisplay": {
                Settings.Global.putString(getContentResolver(),
                        Global.OVERLAY_DISPLAY_DEVICES, "");
                break;
            }

            case "createOverlayDisplay": {
                if (args.length > 1) {
                    Settings.Global.putString(getContentResolver(),
                            Global.OVERLAY_DISPLAY_DEVICES, args[1]);
                } else {
                    Log.i(TAG, "Not enough arguments, expected 2");
                }
                break;
            }

            case "setUnobscuredArea": {
                if (args.length > 5) {
                    Rect unobscuredArea = new Rect(parseInt(args[2]), parseInt(args[3]),
                            parseInt(args[4]), parseInt(args[5]));
                    try {
                        setClusterActivityState(args[1],
                                ClusterActivityState.create(true, unobscuredArea).toBundle());
                    } catch (CarNotConnectedException e) {
                        Log.i(TAG, "Failed to set activity state.", e);
                    }
                } else {
                    Log.i(TAG, "wrong format, expected: category left top right bottom");
                }
            }
        }
    }
}
