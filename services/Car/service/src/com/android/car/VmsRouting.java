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

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsLayer;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages all the VMS subscriptions:
 * + Subscriptions to data messages of individual layer + version.
 * + Subscriptions to all data messages.
 * + HAL subscriptions to layer + version.
 */

public class VmsRouting {
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private Map<IBinder, IVmsSubscriberClient> mSubscribers = new ArrayMap<>();

    @GuardedBy("mLock")
    private Set<IBinder> mPassiveSubscribers = new ArraySet<>();

    @GuardedBy("mLock")
    private Map<VmsLayer, Set<IBinder>> mLayerSubscriptions = new ArrayMap<>();

    @GuardedBy("mLock")
    private Map<VmsLayer, Map<Integer, Set<IBinder>>> mLayerSubscriptionsToPublishers =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private int mSequenceNumber = 0;

    /**
     * Add a passive subscription to all data messages.
     *
     * Passive subscribers receive all published data messages, but are not reflected in the
     * subscription state sent to publishers.
     *
     * @param subscriber VMS subscriber to add
     */
    public void addSubscription(IVmsSubscriberClient subscriber) {
        int sequenceNumber;
        synchronized (mLock) {
            if (!mPassiveSubscribers.add(addSubscriber(subscriber))) {
                return;
            }
            sequenceNumber = mSequenceNumber;
        }
        VmsOperationRecorder.get().addPromiscuousSubscription(sequenceNumber);
    }

    /**
     * Remove a passive subscription to all data messages.
     *
     * @param subscriber VMS subscriber to remove
     */
    public void removeSubscription(IVmsSubscriberClient subscriber) {
        int sequenceNumber;
        synchronized (mLock) {
            if (!mPassiveSubscribers.remove(subscriber.asBinder())) {
                return;
            }
            sequenceNumber = mSequenceNumber;
        }
        VmsOperationRecorder.get().removePromiscuousSubscription(sequenceNumber);
    }

    /**
     * Add a subscription to data messages from a VMS layer.
     *
     * @param subscriber VMS subscriber to add
     * @param layer      the layer to subscribe to
     */
    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        int sequenceNumber;
        synchronized (mLock) {
            Set<IBinder> subscribers =
                    mLayerSubscriptions.computeIfAbsent(layer, k -> new ArraySet<>());
            if (!subscribers.add(addSubscriber(subscriber))) {
                return;
            }
            sequenceNumber = ++mSequenceNumber;
        }
        VmsOperationRecorder.get().addSubscription(sequenceNumber, layer);
    }

    /**
     * Remove a subscription to data messages from a VMS layer.
     *
     * @param subscriber VMS subscriber to remove
     * @param layer      the subscribed layer
     */
    public void removeSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        int sequenceNumber;
        synchronized (mLock) {
            Set<IBinder> subscribers =
                    mLayerSubscriptions.getOrDefault(layer, Collections.emptySet());
            if (!subscribers.remove(subscriber.asBinder())) {
                return;
            }
            sequenceNumber = ++mSequenceNumber;

            if (subscribers.isEmpty()) {
                // If a layer has no subscribers, remove it
                mLayerSubscriptions.remove(layer);
            }
        }
        VmsOperationRecorder.get().removeSubscription(sequenceNumber, layer);
    }

    /**
     * Add a subscription to data messages from a VMS layer and a specific publisher.
     *
     * @param subscriber  VMS subscriber to add
     * @param layer       the layer to subscribe to
     * @param publisherId the publisher ID
     */
    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        int sequenceNumber;
        synchronized (mLock) {
            Set<IBinder> subscribers =
                    mLayerSubscriptionsToPublishers.computeIfAbsent(layer, k -> new ArrayMap<>())
                            .computeIfAbsent(publisherId, k -> new ArraySet<>());
            if (!subscribers.add(addSubscriber(subscriber))) {
                return;
            }
            sequenceNumber = ++mSequenceNumber;
        }
        VmsOperationRecorder.get().addSubscription(sequenceNumber, layer);
    }

    /**
     * Remove a subscription to data messages from a VMS layer and a specific publisher.
     *
     * @param subscriber  VMS subscriber to remove
     * @param layer       the subscribed layer
     * @param publisherId the publisher ID
     */
    public void removeSubscription(IVmsSubscriberClient subscriber,
            VmsLayer layer,
            int publisherId) {
        int sequenceNumber;
        synchronized (mLock) {
            Map<Integer, Set<IBinder>> subscribersToPublishers =
                    mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap());

            Set<IBinder> subscribers =
                    subscribersToPublishers.getOrDefault(publisherId, Collections.emptySet());
            if (!subscribers.remove(subscriber.asBinder())) {
                return;
            }
            sequenceNumber = ++mSequenceNumber;

            // If a publisher has no subscribers, remove it
            if (subscribers.isEmpty()) {
                subscribersToPublishers.remove(publisherId);
            }

            // If a layer has no subscribers, remove it
            if (subscribersToPublishers.isEmpty()) {
                mLayerSubscriptionsToPublishers.remove(layer);
            }
        }
        VmsOperationRecorder.get().removeSubscription(sequenceNumber, layer);
    }

    /**
     * Remove all of a subscriber's subscriptions.
     *
     * @param subscriber VMS subscriber to remove
     * @return {@code true} if the subscription state was modified
     */
    public boolean removeDeadSubscriber(IVmsSubscriberClient subscriber) {
        IBinder subscriberBinder = subscriber.asBinder();
        synchronized (mLock) {
            int startSequenceNumber = mSequenceNumber;

            // Remove the subscriber from the loggers.
            removeSubscription(subscriber);

            // Remove the subscriber from all layer-based subscriptions.
            mLayerSubscriptions.entrySet().stream()
                    .filter(e -> e.getValue().contains(subscriberBinder))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet())
                    .forEach(layer -> removeSubscription(subscriber, layer));

            // Remove the subscriber from all publisher-based subscriptions.
            mLayerSubscriptionsToPublishers.entrySet().stream()
                    .flatMap(layer -> layer.getValue().entrySet().stream()
                            .filter(publisher -> publisher.getValue().contains(subscriberBinder))
                            .map(publisher -> Pair.create(layer.getKey(), publisher.getKey())))
                    .collect(Collectors.toSet())
                    .forEach(layerAndPublisher -> removeSubscription(subscriber,
                            layerAndPublisher.first, layerAndPublisher.second));

            // Remove the subscriber from the subscriber index
            mSubscribers.remove(subscriberBinder);

            // If the sequence number was updated, then the subscription state was modified
            return startSequenceNumber != mSequenceNumber;
        }
    }

    /**
     * Returns a list of all the subscribers a data message should be delivered to. This includes
     * subscribers that subscribed to this layer from all publishers, subscribed to this layer
     * from a specific publisher, and passive subscribers.
     *
     * @param layer       The layer of the message.
     * @param publisherId the ID of the client that published the message to be routed.
     * @return a list of the subscribers.
     */
    public Set<IVmsSubscriberClient> getSubscribersForLayerFromPublisher(VmsLayer layer,
            int publisherId) {
        Set<IBinder> subscribers = new HashSet<>();
        synchronized (mLock) {
            // Add the passive subscribers
            subscribers.addAll(mPassiveSubscribers);

            // Add the subscribers which explicitly subscribed to this layer
            subscribers.addAll(mLayerSubscriptions.getOrDefault(layer, Collections.emptySet()));

            // Add the subscribers which explicitly subscribed to this layer and publisher
            subscribers.addAll(
                    mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap())
                            .getOrDefault(publisherId, Collections.emptySet()));
        }
        return subscribers.stream()
                .map(binder -> mSubscribers.get(binder))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * @return {@code true} if there is an explicit subscription to the layer
     */
    public boolean hasLayerSubscriptions(VmsLayer layer) {
        synchronized (mLock) {
            return mLayerSubscriptions.containsKey(layer);
        }
    }

    /**
     * @return {@code true} if there is an explicit subscription to the layer and publisherId
     */
    public boolean hasLayerFromPublisherSubscriptions(VmsLayer layer, int publisherId) {
        synchronized (mLock) {
            return mLayerSubscriptionsToPublishers.containsKey(layer)
                    && mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap())
                    .containsKey(publisherId);
        }
    }

    /**
     * @return a Set of layers and publishers which VMS clients are subscribed to.
     */
    public VmsSubscriptionState getSubscriptionState() {
        synchronized (mLock) {
            return new VmsSubscriptionState(mSequenceNumber,
                    new ArraySet<>(mLayerSubscriptions.keySet()),
                    mLayerSubscriptionsToPublishers.entrySet()
                            .stream()
                            .map(e -> new VmsAssociatedLayer(e.getKey(), e.getValue().keySet()))
                            .collect(Collectors.toSet()));
        }
    }

    private IBinder addSubscriber(IVmsSubscriberClient subscriber) {
        IBinder subscriberBinder = subscriber.asBinder();
        synchronized (mLock) {
            mSubscribers.putIfAbsent(subscriberBinder, subscriber);
        }
        return subscriberBinder;
    }
}
