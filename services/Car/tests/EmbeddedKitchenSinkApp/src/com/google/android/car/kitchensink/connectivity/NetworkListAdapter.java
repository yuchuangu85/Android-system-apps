/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.car.kitchensink.connectivity;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.connectivity.ConnectivityFragment.NetworkItem;

public class NetworkListAdapter extends ArrayAdapter<NetworkItem> {
    private static final String TAG = NetworkListAdapter.class.getSimpleName();

    private Context mContext;
    private NetworkItem[] mNetworkList; // keep list of objects
    private ConnectivityFragment mFragment; // for calling things on button press

    public NetworkListAdapter(Context context,  NetworkItem[] items,
                              ConnectivityFragment fragment) {
        super(context, R.layout.network_item, items);
        mContext = context;
        mFragment = fragment;
        mNetworkList = items;

        Log.i(TAG, "Created NetworkListAdaptor");
    }

    // Returns a list item view for each position
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            vh = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(R.layout.network_item, parent, false);
            vh.netActive = convertView.findViewById(R.id.network_active);
            vh.netId = convertView.findViewById(R.id.network_id);
            vh.netType = convertView.findViewById(R.id.network_type);
            vh.netState = convertView.findViewById(R.id.network_state);
            vh.connected = convertView.findViewById(R.id.network_connected);
            vh.available = convertView.findViewById(R.id.network_available);
            vh.roaming = convertView.findViewById(R.id.network_roaming);
            vh.netIface = convertView.findViewById(R.id.network_iface);
            vh.hwAddress = convertView.findViewById(R.id.hw_address);
            vh.ipAddresses = convertView.findViewById(R.id.network_ip_addresses);
            vh.dns = convertView.findViewById(R.id.network_dns);
            vh.domains = convertView.findViewById(R.id.network_domains);
            vh.routes = convertView.findViewById(R.id.network_routes);
            vh.transports = convertView.findViewById(R.id.network_transports);
            vh.capabilities = convertView.findViewById(R.id.network_capabilities);
            vh.bandwidth = convertView.findViewById(R.id.network_bandwidth);
            vh.requestButton = convertView.findViewById(R.id.network_request);
            vh.defaultButton = convertView.findViewById(R.id.network_default);
            vh.reportButton = convertView.findViewById(R.id.network_report);

            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        // If there's data to fill for the given position in the list
        if (position < getCount()) {
            vh.netId.setText("" + mNetworkList[position].mNetId);
            vh.netType.setText(mNetworkList[position].mType);
            vh.netState.setText(mNetworkList[position].mState);
            vh.connected.setText(mNetworkList[position].mConnected);
            vh.available.setText(mNetworkList[position].mAvailable);
            vh.roaming.setText(mNetworkList[position].mRoaming);
            vh.netIface.setText(mNetworkList[position].mInterfaceName);
            vh.hwAddress.setText(mNetworkList[position].mHwAddress);
            vh.ipAddresses.setText(mNetworkList[position].mIpAddresses);
            vh.dns.setText(mNetworkList[position].mDnsAddresses);
            vh.domains.setText(mNetworkList[position].mDomains);
            vh.routes.setText(mNetworkList[position].mRoutes);
            vh.transports.setText(mNetworkList[position].mTransports);
            vh.capabilities.setText(mNetworkList[position].mCapabilities);
            vh.bandwidth.setText(mNetworkList[position].mBandwidth);

            // Active request indicator
            vh.netActive.setBackgroundColor(mNetworkList[position].mRequested
                    ? Color.parseColor("#5fdd6e")
                    : Color.parseColor("#ff3d3d"));

            // Request to track button
            setToggleButton(position, vh.requestButton, mNetworkList[position].mRequested,
                    "Release", "Request", this::onRequestClicked);

            // Process default button
            setToggleButton(position, vh.defaultButton, mNetworkList[position].mDefault,
                    "Remove Default", "Set Default", this::onDefaultClicked);

            // Report network button
            setPositionTaggedCallback(position, vh.reportButton, this::onReportClicked);
        }

        // Alternate table row background color to make it easier to view
        convertView.setBackgroundColor(((position % 2) != 0)
                ? Color.parseColor("#2A2E2D")
                : Color.parseColor("#1E1E1E"));

        return convertView;
    }

    // Tags a button with its element position and assigned it's callback. The callback can then
    // get the tag and use it as a position to know which data is associated with it
    private void setPositionTaggedCallback(int position, Button button, View.OnClickListener l) {
        button.setTag(position);
        button.setOnClickListener(l);
    }

    private void setToggleButton(int position, Button button, boolean on, String ifOn, String ifOff,
            View.OnClickListener l) {
        // Manage button text based on status
        if (on) {
            button.setText(ifOn);
        } else {
            button.setText(ifOff);
        }
        setPositionTaggedCallback(position, button, l);
    }

    private void onRequestClicked(View view) {
        int position = (int) view.getTag();
        if (mNetworkList[position].mRequested) {
            mFragment.releaseNetworkById(mNetworkList[position].mNetId);
            mNetworkList[position].mRequested = false;
        } else {
            mFragment.requestNetworkById(mNetworkList[position].mNetId);
            mNetworkList[position].mRequested = true;
        }
        notifyDataSetChanged();
    }

    private void onDefaultClicked(View view) {
        int position = (int) view.getTag();
        if (mNetworkList[position].mDefault) {
            mFragment.clearBoundNetwork();
            mNetworkList[position].mDefault = false;
        } else {
            for (int i = 0; i < mNetworkList.length; i++) {
                if (i != position) {
                    mNetworkList[i].mDefault = false;
                }
            }
            mFragment.bindToNetwork(mNetworkList[position].mNetId);
            mNetworkList[position].mDefault = true;
        }
        notifyDataSetChanged();
    }

    private void onReportClicked(View view) {
        int position = (int) view.getTag();
        mFragment.reportNetworkbyId(mNetworkList[position].mNetId);
    }

    public void refreshNetworks(NetworkItem[] networksIn) {
        mNetworkList = networksIn;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mNetworkList.length;
    }

    static class ViewHolder {
        public View netActive;
        public TextView netId;
        public TextView netType;
        public TextView netState;
        public TextView connected;
        public TextView available;
        public TextView roaming;
        public TextView netIface;
        public TextView hwAddress;
        public TextView ipAddresses;
        public TextView dns;
        public TextView domains;
        public TextView routes;
        public TextView transports;
        public TextView capabilities;
        public TextView bandwidth;
        public Button requestButton;
        public Button defaultButton;
        public Button reportButton;
    }
}
