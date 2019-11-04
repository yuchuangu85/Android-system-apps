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

package com.android.car;

import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsPublisherService;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsSubscriptionState;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Receives HAL updates by implementing VmsHalService.VmsHalListener.
 * Binds to publishers and configures them to use this service.
 * Notifies publishers of subscription changes.
 */
public class VmsPublisherService implements CarServiceBase, VmsClientManager.ConnectionListener {
    private static final boolean DBG = true;
    private static final String TAG = "VmsPublisherService";

    @VisibleForTesting
    static final String PACKET_COUNT_FORMAT = "Packet count for layer %s: %d\n";

    @VisibleForTesting
    static final String PACKET_SIZE_FORMAT = "Total packet size for layer %s: %d (bytes)\n";

    @VisibleForTesting
    static final String PACKET_FAILURE_COUNT_FORMAT =
            "Total packet failure count for layer %s from %s to %s: %d\n";

    @VisibleForTesting
    static final String PACKET_FAILURE_SIZE_FORMAT =
            "Total packet failure size for layer %s from %s to %s: %d (bytes)\n";

    private final Context mContext;
    private final VmsClientManager mClientManager;
    private final VmsBrokerService mBrokerService;
    private final Map<String, PublisherProxy> mPublisherProxies = Collections.synchronizedMap(
            new ArrayMap<>());

    @GuardedBy("mPacketCounts")
    private final Map<VmsLayer, PacketCountAndSize> mPacketCounts = new ArrayMap<>();
    @GuardedBy("mPacketFailureCounts")
    private final Map<PacketFailureKey, PacketCountAndSize> mPacketFailureCounts = new ArrayMap<>();

    // PacketCountAndSize keeps track of the cumulative size and number of packets of a specific
    // VmsLayer that we have seen.
    private class PacketCountAndSize {
        long mCount;
        long mSize;
    }

    // PacketFailureKey is a triple of the VmsLayer, the publisher and subscriber for which a packet
    // failed to be sent.
    private class PacketFailureKey {
        VmsLayer mVmsLayer;
        String mPublisher;
        String mSubscriber;

        PacketFailureKey(VmsLayer vmsLayer, String publisher, String subscriber) {
            mVmsLayer = vmsLayer;
            mPublisher = publisher;
            mSubscriber = subscriber;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PacketFailureKey)) {
                return false;
            }

            PacketFailureKey otherKey = (PacketFailureKey) o;
            return Objects.equals(mVmsLayer, otherKey.mVmsLayer) && Objects.equals(mPublisher,
                    otherKey.mPublisher) && Objects.equals(mSubscriber, otherKey.mSubscriber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mVmsLayer, mPublisher, mSubscriber);
        }
    }

    public VmsPublisherService(
            Context context,
            VmsBrokerService brokerService,
            VmsClientManager clientManager) {
        mContext = context;
        mClientManager = clientManager;
        mBrokerService = brokerService;
        mClientManager.registerConnectionListener(this);
    }

    @Override
    public void init() {}

    @Override
    public void release() {
        mPublisherProxies.values().forEach(PublisherProxy::unregister);
        mPublisherProxies.clear();
    }

    @Override
    public void dump(PrintWriter writer) {
        dumpMetrics(writer);
    }

    @Override
    public void dumpMetrics(PrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        writer.println("mPublisherProxies: " + mPublisherProxies.size());
        synchronized (mPacketCounts) {
            for (Map.Entry<VmsLayer, PacketCountAndSize> entry : mPacketCounts.entrySet()) {
                VmsLayer layer = entry.getKey();
                PacketCountAndSize countAndSize = entry.getValue();
                writer.format(PACKET_COUNT_FORMAT, layer, countAndSize.mCount);
                writer.format(PACKET_SIZE_FORMAT, layer, countAndSize.mSize);
            }
        }
        synchronized (mPacketFailureCounts) {
            for (Map.Entry<PacketFailureKey, PacketCountAndSize> entry :
                    mPacketFailureCounts.entrySet()) {
                PacketFailureKey key = entry.getKey();
                PacketCountAndSize countAndSize = entry.getValue();
                VmsLayer layer = key.mVmsLayer;
                String publisher = key.mPublisher;
                String subscriber = key.mSubscriber;
                writer.format(PACKET_FAILURE_COUNT_FORMAT, layer, publisher, subscriber,
                        countAndSize.mCount);
                writer.format(PACKET_FAILURE_SIZE_FORMAT, layer, publisher, subscriber,
                        countAndSize.mSize);
            }
        }
    }

    @Override
    public void onClientConnected(String publisherName, IBinder binder) {
        if (DBG) Log.d(TAG, "onClientConnected: " + publisherName);
        IBinder publisherToken = new Binder();
        IVmsPublisherClient publisherClient = IVmsPublisherClient.Stub.asInterface(binder);

        PublisherProxy publisherProxy = new PublisherProxy(publisherName, publisherToken,
                publisherClient);
        publisherProxy.register();
        try {
            publisherClient.setVmsPublisherService(publisherToken, publisherProxy);
        } catch (Throwable e) {
            Log.e(TAG, "unable to configure publisher: " + publisherName, e);
            return;
        }

        PublisherProxy existingProxy = mPublisherProxies.put(publisherName, publisherProxy);
        if (existingProxy != null) {
            existingProxy.unregister();
        }
    }

    @Override
    public void onClientDisconnected(String publisherName) {
        if (DBG) Log.d(TAG, "onClientDisconnected: " + publisherName);
        PublisherProxy proxy = mPublisherProxies.remove(publisherName);
        if (proxy != null) {
            proxy.unregister();
        }
    }

    private class PublisherProxy extends IVmsPublisherService.Stub implements
            VmsBrokerService.PublisherListener {
        private final String mName;
        private final IBinder mToken;
        private final IVmsPublisherClient mPublisherClient;
        private boolean mConnected;

        PublisherProxy(String name, IBinder token,
                IVmsPublisherClient publisherClient) {
            this.mName = name;
            this.mToken = token;
            this.mPublisherClient = publisherClient;
        }

        void register() {
            if (DBG) Log.d(TAG, "register: " + mName);
            mConnected = true;
            mBrokerService.addPublisherListener(this);
        }

        void unregister() {
            if (DBG) Log.d(TAG, "unregister: " + mName);
            mConnected = false;
            mBrokerService.removePublisherListener(this);
            mBrokerService.removeDeadPublisher(mToken);
        }

        @Override
        public void setLayersOffering(IBinder token, VmsLayersOffering offering) {
            assertPermission(token);
            mBrokerService.setPublisherLayersOffering(token, offering);
        }

        private void incrementPacketCount(VmsLayer layer, long size) {
            synchronized (mPacketCounts) {
                PacketCountAndSize countAndSize = mPacketCounts.computeIfAbsent(layer,
                        i -> new PacketCountAndSize());
                countAndSize.mCount++;
                countAndSize.mSize += size;
            }
        }

        private void incrementPacketFailure(VmsLayer layer, String publisher, String subscriber,
                long size) {
            synchronized (mPacketFailureCounts) {
                PacketFailureKey key = new PacketFailureKey(layer, publisher, subscriber);
                PacketCountAndSize countAndSize = mPacketFailureCounts.computeIfAbsent(key,
                        i -> new PacketCountAndSize());
                countAndSize.mCount++;
                countAndSize.mSize += size;
            }
        }

        @Override
        public void publish(IBinder token, VmsLayer layer, int publisherId, byte[] payload) {
            assertPermission(token);
            if (DBG) {
                Log.d(TAG, String.format("Publishing to %s as %d (%s)", layer, publisherId, mName));
            }

            if (layer == null) {
                return;
            }

            int payloadLength = payload != null ? payload.length : 0;
            incrementPacketCount(layer, payloadLength);

            // Send the message to subscribers
            Set<IVmsSubscriberClient> listeners =
                    mBrokerService.getSubscribersForLayerFromPublisher(layer, publisherId);

            if (DBG) Log.d(TAG, String.format("Number of subscribers: %d", listeners.size()));

            if (listeners.size() == 0) {
                // An empty string for the last argument is a special value signalizing zero
                // subscribers for the VMS_PACKET_FAILURE_REPORTED atom.
                incrementPacketFailure(layer, mName, "", payloadLength);
            }

            for (IVmsSubscriberClient listener : listeners) {
                try {
                    listener.onVmsMessageReceived(layer, payload);
                } catch (RemoteException ex) {
                    String subscriberName = mBrokerService.getPackageName(listener);
                    incrementPacketFailure(layer, mName, subscriberName, payloadLength);
                    Log.e(TAG, String.format("Unable to publish to listener: %s", subscriberName));
                }
            }
        }

        @Override
        public VmsSubscriptionState getSubscriptions() {
            assertPermission();
            return mBrokerService.getSubscriptionState();
        }

        @Override
        public int getPublisherId(byte[] publisherInfo) {
            assertPermission();
            return mBrokerService.getPublisherId(publisherInfo);
        }

        @Override
        public void onSubscriptionChange(VmsSubscriptionState subscriptionState) {
            try {
                mPublisherClient.onVmsSubscriptionChange(subscriptionState);
            } catch (Throwable e) {
                Log.e(TAG, String.format("Unable to send subscription state to: %s", mName), e);
            }
        }

        private void assertPermission(IBinder publisherToken) {
            if (mToken != publisherToken) {
                throw new SecurityException("Invalid publisher token");
            }
            assertPermission();
        }

        private void assertPermission() {
            if (!mConnected) {
                throw new SecurityException("Publisher has been disconnected");
            }
            ICarImpl.assertVmsPublisherPermission(mContext);
        }
    }
}
