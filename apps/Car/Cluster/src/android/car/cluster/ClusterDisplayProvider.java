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

package android.car.cluster;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.DisplayAddress;

/**
 * This class provides a display for instrument cluster renderer.
 * <p>
 * By default it will try to provide physical secondary display if it is connected, if secondary
 * display is not connected during creation of this class then it will start networked virtual
 * display and listens for incoming connections.
 *
 * @see {@link NetworkedVirtualDisplay}
 */
public class ClusterDisplayProvider {
    private static final String TAG = "Cluster.DisplayProvider";

    private static final String RO_CLUSTER_DISPLAY_PORT = "ro.car.cluster.displayport";
    private static final String PERSIST_CLUSTER_DISPLAY_PORT =
            "persist.car.cluster.displayport";
    private static final int NETWORKED_DISPLAY_WIDTH = 1280;
    private static final int NETWORKED_DISPLAY_HEIGHT = 720;
    private static final int NETWORKED_DISPLAY_DPI = 320;

    private final DisplayListener mListener;
    private final DisplayManager mDisplayManager;

    private NetworkedVirtualDisplay mNetworkedVirtualDisplay;
    private int mClusterDisplayId = -1;

    ClusterDisplayProvider(Context context, DisplayListener clusterDisplayListener) {
        mListener = clusterDisplayListener;
        mDisplayManager = context.getSystemService(DisplayManager.class);

        Display clusterDisplay = getInstrumentClusterDisplay(mDisplayManager);
        if (clusterDisplay != null) {
            Log.i(TAG, String.format("Found display: %s (id: %d, owner: %s)",
                    clusterDisplay.getName(), clusterDisplay.getDisplayId(),
                    clusterDisplay.getOwnerPackageName()));
            mClusterDisplayId = clusterDisplay.getDisplayId();
            clusterDisplayListener.onDisplayAdded(clusterDisplay.getDisplayId());
            trackClusterDisplay(null /* no need to track display by name */);
        } else {
            Log.i(TAG, "No physical cluster display found, starting network display");
            setupNetworkDisplay(context);
        }
    }

    private void setupNetworkDisplay(Context context) {
        mNetworkedVirtualDisplay = new NetworkedVirtualDisplay(context,
                NETWORKED_DISPLAY_WIDTH, NETWORKED_DISPLAY_HEIGHT, NETWORKED_DISPLAY_DPI);
        String displayName = mNetworkedVirtualDisplay.start();
        trackClusterDisplay(displayName);
    }

    private void trackClusterDisplay(@Nullable String displayName) {
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                boolean clusterDisplayAdded = false;

                if (displayName == null && mClusterDisplayId == -1) {
                    mClusterDisplayId = displayId;
                    clusterDisplayAdded = true;
                } else {
                    Display display = mDisplayManager.getDisplay(displayId);
                    if (display != null && TextUtils.equals(display.getName(), displayName)) {
                        mClusterDisplayId = displayId;
                        clusterDisplayAdded = true;
                    }
                }

                if (clusterDisplayAdded) {
                    mListener.onDisplayAdded(displayId);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                if (displayId == mClusterDisplayId) {
                    mClusterDisplayId = -1;
                    mListener.onDisplayRemoved(displayId);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == mClusterDisplayId) {
                    mListener.onDisplayChanged(displayId);
                }
            }

        }, null);
    }

    private static Display getInstrumentClusterDisplay(DisplayManager displayManager) {
        Display[] displays = displayManager.getDisplays();
        Log.d(TAG, "There are currently " + displays.length + " displays connected.");

        final int displayPortPrimary = 0;  // primary port should not be instrument cluster.
        int displayPort = SystemProperties.getInt(PERSIST_CLUSTER_DISPLAY_PORT,
                displayPortPrimary);
        if (displayPort == displayPortPrimary) {
            displayPort = SystemProperties.getInt(RO_CLUSTER_DISPLAY_PORT,
                    displayPortPrimary);
            if (displayPort == displayPortPrimary) {
                return null;
            }
        }
        // match port for system display ( = null getOwnerPackageName())
        // with separate check for main display as main display should be never picked up.
        for (Display display : displays) {
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY
                    && display.getOwnerPackageName() == null
                    && display.getAddress() != null
                    && display.getAddress() instanceof DisplayAddress.Physical) {
                final byte port = ((DisplayAddress.Physical) display.getAddress()).getPort();
                if (displayPort == port) {
                    return display;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + " clusterDisplayId = " + mClusterDisplayId
                + "}";
    }
}
