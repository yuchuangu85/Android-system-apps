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

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

public class NavigationFragment extends Fragment {
    private static final String TAG = "Cluster.NavFragment";

    private SurfaceView mSurfaceView;
    private DisplayManager mDisplayManager;
    private Rect mUnobscuredBounds;
    private MainClusterActivity mMainClusterActivity;
    private ClusterViewModel mViewModel;
    private ProgressBar mProgressBar;
    private TextView mMessage;


    // Static because we want to keep alive this virtual display when navigating through
    // ViewPager (this fragment gets dynamically destroyed and created)
    private static VirtualDisplay mVirtualDisplay;
    private static int mRegisteredNavDisplayId = Display.INVALID_DISPLAY;
    private boolean mNavigationDisplayUpdatePending = false;

    public NavigationFragment() {
        // Required empty public constructor
    }


    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            int navDisplayId = getVirtualDisplayId();
            Log.i(TAG, "onDisplayAdded, displayId: " + displayId
                    + ", navigation display id: " + navDisplayId);

            if (navDisplayId == displayId) {
                mRegisteredNavDisplayId = displayId;
                updateNavigationDisplay();
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            if (mRegisteredNavDisplayId == displayId) {
                mRegisteredNavDisplayId = Display.INVALID_DISPLAY;
                updateNavigationDisplay();
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {}
    };

    private void updateNavigationDisplay() {
        if (mMainClusterActivity == null) {
            // Not attached to the activity yet. Let's wait.
            mNavigationDisplayUpdatePending = true;
            return;
        }

        mNavigationDisplayUpdatePending = false;
        mMainClusterActivity.updateNavDisplay(new MainClusterActivity.VirtualDisplay(
                mRegisteredNavDisplayId, mUnobscuredBounds));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mMainClusterActivity = (MainClusterActivity) context;
        if (mNavigationDisplayUpdatePending) {
            updateNavigationDisplay();
        }
    }

    @Override
    public void onDetach() {
        mMainClusterActivity = null;
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        ViewModelProvider provider = ViewModelProviders.of(requireActivity());
        mViewModel = provider.get(ClusterViewModel.class);

        mDisplayManager = getActivity().getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, new Handler());

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_navigation, container, false);

        mSurfaceView = root.findViewById(R.id.nav_surface);
        mSurfaceView.getHolder().addCallback(new Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "surfaceCreated, holder: " + holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.i(TAG, "surfaceChanged, holder: " + holder + ", size:" + width + "x" + height
                        + ", format:" + format);

                // Create dummy unobscured area to report to navigation activity.
                int obscuredWidth = (int) getResources()
                        .getDimension(R.dimen.speedometer_overlap_width);
                int obscuredHeight = (int) getResources()
                        .getDimension(R.dimen.navigation_gradient_height);
                mUnobscuredBounds = new Rect(
                        obscuredWidth,          /* left: size of gauge */
                        obscuredHeight,         /* top: gradient */
                        width - obscuredWidth,  /* right: size of the display - size of gauge */
                        height - obscuredHeight /* bottom: size of display - gradient */
                );

                if (mVirtualDisplay == null) {
                    mVirtualDisplay = createVirtualDisplay(holder.getSurface(), width, height);
                } else {
                    mVirtualDisplay.setSurface(holder.getSurface());
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.i(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                        + " display, surface: " + holder.getSurface());
                // detaching surface is similar to turning off the display
                mVirtualDisplay.setSurface(null);
            }
        });
        mProgressBar = root.findViewById(R.id.progress_bar);
        mMessage = root.findViewById(R.id.message);

        mViewModel.getNavigationActivityState().observe(this, state -> {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "State: " + state);
            }
            mProgressBar.setVisibility(state == ClusterViewModel.NavigationActivityState.LOADING
                    ? View.VISIBLE : View.INVISIBLE);
            mMessage.setVisibility(state == ClusterViewModel.NavigationActivityState.NOT_SELECTED
                    ? View.VISIBLE : View.INVISIBLE);
        });

        return root;
    }

    private VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) {
        Log.i(TAG, "createVirtualDisplay, surface: " + surface + ", width: " + width
                + "x" + height);
        return mDisplayManager.createVirtualDisplay("Cluster-App-VD", width, height, 160, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    private int getVirtualDisplayId() {
        return (mVirtualDisplay != null && mVirtualDisplay.getDisplay() != null)
                ? mVirtualDisplay.getDisplay().getDisplayId() : Display.INVALID_DISPLAY;
    }
}
