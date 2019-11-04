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
package com.google.android.car.kitchensink.projection;

import android.car.CarProjectionManager;
import android.car.CarProjectionManager.ProjectionStatusListener;
import android.car.projection.ProjectionStatus;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;
import com.google.common.collect.ImmutableMap;

import java.util.List;

/**
 * Reports information about the current projection status.
 */
public class ProjectionFragment extends Fragment {
    private KitchenSinkActivity mActivity;
    private CarProjectionManager mCarProjectionManager;

    private TextView mCurrentProjectionStatus;
    private TextView mCurrentProjectionPackage;
    private LinearLayout mCurrentProjectionDetails;

    private static final ImmutableMap<Integer, String> STATE_TO_STRING = ImmutableMap.of(
            ProjectionStatus.PROJECTION_STATE_INACTIVE, "Inactive",
            ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT, "Ready to project",
            ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND, "Foreground",
            ProjectionStatus.PROJECTION_STATE_ACTIVE_BACKGROUND, "Background");

    private static final ImmutableMap<Integer, String> TRANSPORT_TO_STRING = ImmutableMap.of(
            ProjectionStatus.PROJECTION_TRANSPORT_NONE, "None",
            ProjectionStatus.PROJECTION_TRANSPORT_USB, "USB",
            ProjectionStatus.PROJECTION_TRANSPORT_WIFI, "WiFi");

    private class KitchenSinkProjectionStatusListener implements ProjectionStatusListener {
        @Override
        public void onProjectionStatusChanged(
                int state,
                String packageName,
                List<ProjectionStatus> details) {
            mCurrentProjectionStatus.setText(STATE_TO_STRING.get(state));
            mCurrentProjectionPackage.setText(packageName);
            mCurrentProjectionDetails.removeAllViews();
            for (ProjectionStatus detail : details) {
                LinearLayout detailLayout =
                        (LinearLayout)
                                getLayoutInflater()
                                        .inflate(R.layout.projection_status_details, null);

                TextView detailPackage = detailLayout.findViewById(R.id.projection_detail_package);
                detailPackage.setText(detail.getPackageName());

                TextView detailState = detailLayout.findViewById(R.id.projection_detail_state);
                detailState.setText(STATE_TO_STRING.get(detail.getState()));

                TextView detailTransport =
                        detailLayout.findViewById(R.id.projection_detail_transport);
                detailTransport.setText(TRANSPORT_TO_STRING.get(detail.getTransport()));

                for (ProjectionStatus.MobileDevice device : detail.getConnectedMobileDevices()) {
                    LinearLayout deviceLayout =
                            (LinearLayout)
                                    getLayoutInflater()
                                            .inflate(R.layout.projection_status_device, null);

                    TextView deviceId = deviceLayout.findViewById(R.id.projection_device_id);
                    deviceId.setText(String.valueOf(device.getId()));

                    TextView deviceName = deviceLayout.findViewById(R.id.projection_device_name);
                    deviceName.setText(device.getName());

                    LinearLayout deviceTransports =
                            deviceLayout.findViewById(R.id.projection_device_transports);
                    for (Integer transport : device.getAvailableTransports()) {
                        TextView transportView = new TextView(mActivity);
                        transportView.setText(TRANSPORT_TO_STRING.get(transport));

                        deviceTransports.addView(transportView);
                    }

                    TextView deviceProjecting =
                            deviceLayout.findViewById(R.id.projection_device_projecting);
                    deviceProjecting.setText(String.valueOf(device.isProjecting()));

                    detailLayout.addView(deviceLayout);
                }
                mCurrentProjectionDetails.addView(detailLayout);
            }
        }
    }

    private final KitchenSinkProjectionStatusListener mProjectionListener =
            new KitchenSinkProjectionStatusListener();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mActivity = (KitchenSinkActivity) getActivity();
        mCarProjectionManager = mActivity.getProjectionManager();
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.projection_status, container, false);

        mCurrentProjectionStatus = (TextView) layout.findViewById(R.id.current_projection_status);
        mCurrentProjectionPackage = (TextView) layout.findViewById(R.id.current_projection_package);
        mCurrentProjectionDetails =
                (LinearLayout) layout.findViewById(R.id.current_projection_details);

        return layout;
    }

    @Override
    public void onStart() {
        mCarProjectionManager.registerProjectionStatusListener(mProjectionListener);
        super.onStart();
    }

    @Override
    public void onStop() {
        mCarProjectionManager.unregisterProjectionStatusListener(mProjectionListener);
        super.onStop();
    }
}
