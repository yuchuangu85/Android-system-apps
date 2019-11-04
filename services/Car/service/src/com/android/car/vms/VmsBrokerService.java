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

package com.android.car.vms;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.android.car.VmsLayersAvailability;
import com.android.car.VmsPublishersInfo;
import com.android.car.VmsRouting;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

/**
 * Broker service facilitating subscription handling and message passing between
 * VmsPublisherService, VmsSubscriberService, and VmsHalService.
 */
public class VmsBrokerService {
    private static final boolean DBG = true;
    private static final String TAG = "VmsBrokerService";

    @VisibleForTesting
    static final String HAL_CLIENT = "HalClient";

    @VisibleForTesting
    static final String UNKNOWN_PACKAGE = "UnknownPackage";

    private CopyOnWriteArrayList<PublisherListener> mPublisherListeners =
            new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<SubscriberListener> mSubscriberListeners =
            new CopyOnWriteArrayList<>();
    private PackageManager mPackageManager;
    private IntSupplier mGetCallingPid;
    private IntSupplier mGetCallingUid;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final VmsRouting mRouting = new VmsRouting();
    @GuardedBy("mLock")
    private final Map<IBinder, String> mBinderPackage = new HashMap<>();
    @GuardedBy("mLock")
    private final Map<IBinder, Map<Integer, VmsLayersOffering>> mOfferings = new HashMap<>();
    @GuardedBy("mLock")
    private final VmsLayersAvailability mAvailableLayers = new VmsLayersAvailability();
    @GuardedBy("mLock")
    private final VmsPublishersInfo mPublishersInfo = new VmsPublishersInfo();

    /**
     * The VMS publisher service implements this interface to receive publisher callbacks.
     */
    public interface PublisherListener {
        /**
         * Callback triggered when publisher subscription state changes.
         *
         * @param subscriptionState Current subscription state.
         */
        void onSubscriptionChange(VmsSubscriptionState subscriptionState);
    }

    /**
     * The VMS subscriber service implements this interface to receive subscriber callbacks.
     */
    public interface SubscriberListener {
        /**
         * Callback triggered when data is published for a given layer.
         *
         * @param layer       Layer data is being published for
         * @param publisherId Publisher of data
         * @param payload     Layer data
         */
        void onMessageReceived(VmsLayer layer, int publisherId, byte[] payload);

        /**
         * Callback triggered when the layers available for subscription changes.
         *
         * @param availableLayers Current layer availability
         */
        void onLayersAvailabilityChange(VmsAvailableLayers availableLayers);
    }

    /**
     * Constructs new broker service.
     */
    public VmsBrokerService(PackageManager packageManager) {
        this(packageManager, Binder::getCallingPid, Binder::getCallingUid);
    }

    @VisibleForTesting
    VmsBrokerService(PackageManager packageManager, IntSupplier getCallingPid,
            IntSupplier getCallingUid) {
        if (DBG) Log.d(TAG, "Started VmsBrokerService!");
        mPackageManager = packageManager;
        mGetCallingPid = getCallingPid;
        mGetCallingUid = getCallingUid;
    }

    /**
     * Adds a listener for publisher callbacks.
     *
     * @param listener Publisher callback listener
     */
    public void addPublisherListener(PublisherListener listener) {
        mPublisherListeners.add(listener);
    }

    /**
     * Adds a listener for subscriber callbacks.
     *
     * @param listener Subscriber callback listener
     */
    public void addSubscriberListener(SubscriberListener listener) {
        mSubscriberListeners.add(listener);
    }

    /**
     * Removes a listener for publisher callbacks.
     *
     * @param listener Publisher callback listener
     */
    public void removePublisherListener(PublisherListener listener) {
        mPublisherListeners.remove(listener);
    }

    /**
     * Removes a listener for subscriber callbacks.
     *
     * @param listener Subscriber callback listener
     */
    public void removeSubscriberListener(SubscriberListener listener) {
        mSubscriberListeners.remove(listener);
    }

    /**
     * Adds a subscription to all layers.
     *
     * @param subscriber Subscriber client to send layer data
     */
    public void addSubscription(IVmsSubscriberClient subscriber) {
        synchronized (mLock) {
            mRouting.addSubscription(subscriber);
            // Add mapping from binder to package name of subscriber.
            mBinderPackage.computeIfAbsent(subscriber.asBinder(), k -> getCallingPackage());
        }
    }

    /**
     * Removes a subscription to all layers.
     *
     * @param subscriber Subscriber client to remove subscription for
     */
    public void removeSubscription(IVmsSubscriberClient subscriber) {
        synchronized (mLock) {
            mRouting.removeSubscription(subscriber);
        }
    }

    /**
     * Adds a layer subscription.
     *
     * @param subscriber Subscriber client to send layer data
     * @param layer      Layer to send
     */
    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        boolean firstSubscriptionForLayer;
        if (DBG) Log.d(TAG, "Checking for first subscription. Layer: " + layer);
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            firstSubscriptionForLayer = !mRouting.hasLayerSubscriptions(layer);

            // Add the listeners subscription to the layer
            mRouting.addSubscription(subscriber, layer);

            // Add mapping from binder to package name of subscriber.
            mBinderPackage.computeIfAbsent(subscriber.asBinder(), k -> getCallingPackage());
        }
        if (firstSubscriptionForLayer) {
            notifyOfSubscriptionChange();
        }
    }

    /**
     * Removes a layer subscription.
     *
     * @param subscriber Subscriber client to remove subscription for
     * @param layer      Layer to remove
     */
    public void removeSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        boolean layerHasSubscribers;
        synchronized (mLock) {
            if (!mRouting.hasLayerSubscriptions(layer)) {
                if (DBG) Log.d(TAG, "Trying to remove a layer with no subscription: " + layer);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeSubscription(subscriber, layer);

            // Check if publishers need to be notified about this change in subscriptions.
            layerHasSubscribers = mRouting.hasLayerSubscriptions(layer);
        }
        if (!layerHasSubscribers) {
            notifyOfSubscriptionChange();
        }
    }

    /**
     * Adds a publisher-specific layer subscription.
     *
     * @param subscriber  Subscriber client to send layer data
     * @param layer       Layer to send
     * @param publisherId Publisher of layer
     */
    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        boolean firstSubscriptionForLayer;
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            firstSubscriptionForLayer = !(mRouting.hasLayerSubscriptions(layer)
                    || mRouting.hasLayerFromPublisherSubscriptions(layer, publisherId));

            // Add the listeners subscription to the layer
            mRouting.addSubscription(subscriber, layer, publisherId);

            // Add mapping from binder to package name of subscriber.
            mBinderPackage.computeIfAbsent(subscriber.asBinder(), k -> getCallingPackage());
        }
        if (firstSubscriptionForLayer) {
            notifyOfSubscriptionChange();
        }
    }

    /**
     * Removes a publisher-specific layer subscription.
     *
     * @param subscriber  Subscriber client to remove subscription for
     * @param layer       Layer to remove
     * @param publisherId Publisher of layer
     */
    public void removeSubscription(IVmsSubscriberClient subscriber, VmsLayer layer,
            int publisherId) {
        boolean layerHasSubscribers;
        synchronized (mLock) {
            if (!mRouting.hasLayerFromPublisherSubscriptions(layer, publisherId)) {
                Log.i(TAG, "Trying to remove a layer with no subscription: "
                        + layer + ", publisher ID:" + publisherId);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeSubscription(subscriber, layer, publisherId);

            // Check if publishers need to be notified about this change in subscriptions.
            layerHasSubscribers = mRouting.hasLayerSubscriptions(layer)
                    || mRouting.hasLayerFromPublisherSubscriptions(layer, publisherId);
        }
        if (!layerHasSubscribers) {
            notifyOfSubscriptionChange();
        }
    }

    /**
     * Removes a disconnected subscriber's subscriptions
     *
     * @param subscriber Subscriber that was disconnected
     */
    public void removeDeadSubscriber(IVmsSubscriberClient subscriber) {
        boolean subscriptionStateChanged;
        synchronized (mLock) {
            subscriptionStateChanged = mRouting.removeDeadSubscriber(subscriber);

            // Remove mapping from binder to package name of subscriber.
            mBinderPackage.remove(subscriber.asBinder());
        }
        if (subscriptionStateChanged) {
            notifyOfSubscriptionChange();
        }
    }

    /**
     * Gets all subscribers for a specific layer/publisher combination.
     *
     * @param layer       Layer to query
     * @param publisherId Publisher of layer
     */
    public Set<IVmsSubscriberClient> getSubscribersForLayerFromPublisher(VmsLayer layer,
            int publisherId) {
        synchronized (mLock) {
            return mRouting.getSubscribersForLayerFromPublisher(layer, publisherId);
        }
    }

    /**
     * Gets the state of all layer subscriptions.
     */
    public VmsSubscriptionState getSubscriptionState() {
        synchronized (mLock) {
            return mRouting.getSubscriptionState();
        }
    }

    /**
     * Assigns an idempotent ID for publisherInfo and stores it. The idempotency in this case means
     * that the same publisherInfo will always, within a trip of the vehicle, return the same ID.
     * The publisherInfo should be static for a binary and should only change as part of a software
     * update. The publisherInfo is a serialized proto message which VMS clients can interpret.
     */
    public int getPublisherId(byte[] publisherInfo) {
        if (DBG) Log.i(TAG, "Getting publisher static ID");
        synchronized (mLock) {
            return mPublishersInfo.getIdForInfo(publisherInfo);
        }
    }

    /**
     * Gets the publisher information data registered in {@link #getPublisherId(byte[])}
     *
     * @param publisherId Publisher ID to query
     * @return Publisher information
     */
    public byte[] getPublisherInfo(int publisherId) {
        if (DBG) Log.i(TAG, "Getting information for publisher ID: " + publisherId);
        synchronized (mLock) {
            return mPublishersInfo.getPublisherInfo(publisherId);
        }
    }

    /**
     * Sets the layers offered by the publisher with the given publisher token.
     *
     * @param publisherToken Identifier token of publisher
     * @param offering       Layers offered by publisher
     */
    public void setPublisherLayersOffering(IBinder publisherToken, VmsLayersOffering offering) {
        synchronized (mLock) {
            Map<Integer, VmsLayersOffering> publisherOfferings = mOfferings.computeIfAbsent(
                    publisherToken, k -> new HashMap<>());
            publisherOfferings.put(offering.getPublisherId(), offering);
            updateLayerAvailability();
        }
        VmsOperationRecorder.get().setPublisherLayersOffering(offering);
        notifyOfAvailabilityChange();
    }

    /**
     * Removes a disconnected publisher's offerings
     *
     * @param publisherToken Identifier token of publisher to be removed
     */
    public void removeDeadPublisher(IBinder publisherToken) {
        synchronized (mLock) {
            mOfferings.remove(publisherToken);
            updateLayerAvailability();
        }
        notifyOfAvailabilityChange();
    }

    /**
     * Gets all layers available for subscription.
     *
     * @return All available layers
     */
    public VmsAvailableLayers getAvailableLayers() {
        synchronized (mLock) {
            return mAvailableLayers.getAvailableLayers();
        }
    }

    /**
     * Gets the package name for a given IVmsSubscriberClient
     */
    public String getPackageName(IVmsSubscriberClient subscriber) {
        synchronized (mLock) {
            return mBinderPackage.get(subscriber.asBinder());
        }
    }

    private void updateLayerAvailability() {
        Set<VmsLayersOffering> allPublisherOfferings = new HashSet<>();
        synchronized (mLock) {
            for (Map<Integer, VmsLayersOffering> offerings : mOfferings.values()) {
                allPublisherOfferings.addAll(offerings.values());
            }
            if (DBG) Log.d(TAG, "New layer availability: " + allPublisherOfferings);
            mAvailableLayers.setPublishersOffering(allPublisherOfferings);
        }
    }

    private void notifyOfSubscriptionChange() {
        if (DBG) Log.d(TAG, "Notifying publishers on subscriptions");

        VmsSubscriptionState subscriptionState = getSubscriptionState();
        // Notify the App publishers
        for (PublisherListener listener : mPublisherListeners) {
            listener.onSubscriptionChange(subscriptionState);
        }
    }

    private void notifyOfAvailabilityChange() {
        if (DBG) Log.d(TAG, "Notifying subscribers on layers availability");

        VmsAvailableLayers availableLayers = getAvailableLayers();
        // Notify the App subscribers
        for (SubscriberListener listener : mSubscriberListeners) {
            listener.onLayersAvailabilityChange(availableLayers);
        }
    }

    // If we're in a binder call, returns back the package name of the caller of the binder call.
    private String getCallingPackage() {
        int callingPid = mGetCallingPid.getAsInt();
        // Since the HAL lives in the same process, if the callingPid is equal to this process's
        // PID, we know it's the HAL client.
        if (callingPid == Process.myPid()) {
            return HAL_CLIENT;
        }
        int callingUid = mGetCallingUid.getAsInt();
        String packageName = mPackageManager.getNameForUid(callingUid);
        if (packageName == null) {
            return UNKNOWN_PACKAGE;
        } else {
            return packageName;
        }
    }
}
