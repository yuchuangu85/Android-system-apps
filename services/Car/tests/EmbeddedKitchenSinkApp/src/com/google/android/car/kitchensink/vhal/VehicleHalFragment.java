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
package com.google.android.car.kitchensink.vhal;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import android.widget.TextView;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VehicleHalFragment extends Fragment {

    private static final String TAG = "CAR.VEHICLEHAL.KS";

    private KitchenSinkActivity mActivity;
    private ListView mListView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.vhal, container, false);
        mActivity = (KitchenSinkActivity) getHost();
        mListView = view.findViewById(R.id.hal_prop_list);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        final IVehicle vehicle;
        try {
            vehicle = Objects.requireNonNull(IVehicle.getService());
        } catch (NullPointerException | RemoteException e) {
            Log.e(TAG, "unable to retrieve Vehicle HAL service", e);
            return;
        }

        final List<VehiclePropConfig> propConfigList;
        try {
            propConfigList = Objects.requireNonNull(vehicle.getAllPropConfigs());
        } catch (NullPointerException | RemoteException e) {
            Log.e(TAG, "unable to retrieve prop configs", e);
            return;
        }

        final List<HalPropertyInfo> supportedProperties = propConfigList.stream()
            .map(HalPropertyInfo::new)
            .sorted()
            .collect(Collectors.toList());

        mListView.setAdapter(new ListAdapter(mActivity, vehicle, supportedProperties));
    }

    private static class HalPropertyInfo implements Comparable<HalPropertyInfo> {

        public final int id;
        public final String name;
        public final VehiclePropConfig config;

        HalPropertyInfo(VehiclePropConfig config) {
            this.config = config;
            id = config.prop;
            name = VehicleProperty.toString(id);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof HalPropertyInfo && ((HalPropertyInfo) other).id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public int compareTo(HalPropertyInfo halPropertyInfo) {
            return name.compareTo(halPropertyInfo.name);
        }

        public String getValue(IVehicle vehicle) {
            String result[] = new String[] {"<unknown>"};

            try {
                VehiclePropValue request = new VehiclePropValue();
                // TODO: add zones support
                request.prop = id;

                // NB: this call is synchronous
                vehicle.get(request, (status, propValue) -> {
                    if (status == StatusCode.OK) {
                        result[0] = propValue.value.toString();
                    }
                });
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "unable to read property " + name, e);
            }

            return result[0];
        }
    }

    private static final class ListAdapter extends ArrayAdapter<HalPropertyInfo> {
        private static final int RESOURCE_ID = R.layout.vhal_listitem;

        // cannot use superclass' LayoutInflater as it is private
        private final LayoutInflater mLayoutInflater;
        private final IVehicle mVehicle;

        ListAdapter(Context context, IVehicle vehicle, List<HalPropertyInfo> properties) {
            super(context, RESOURCE_ID, properties);
            mVehicle = vehicle;
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final HalPropertyInfo item = getItem(position);

            final LinearLayout viewLayout;
            if (convertView != null && convertView instanceof LinearLayout) {
                viewLayout  = (LinearLayout)convertView;
            } else {
                // this is the value used by the superclass's view inflater
                final boolean attachToRoot = false;

                viewLayout =
                        (LinearLayout)mLayoutInflater.inflate(RESOURCE_ID, parent, attachToRoot);
            }

            TextView textString = viewLayout.findViewById(R.id.textString);
            Button infoButton = viewLayout.findViewById(R.id.infoButton);
            Button valueButton = viewLayout.findViewById(R.id.valueButton);

            infoButton.setOnClickListener(btn -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Configuration for " + item.name)
                    .setPositiveButton(android.R.string.yes, (x, y) -> { })
                    .setMessage(item.config.toString())
                    .show();
            });

            valueButton.setOnClickListener(btn -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Value for " + item.name)
                    .setPositiveButton(android.R.string.yes, (x, y) -> { })
                    .setMessage(item.getValue(mVehicle))
                    .show();
            });

            textString.setText(item.toString());
            return viewLayout;
        }
    }
}
