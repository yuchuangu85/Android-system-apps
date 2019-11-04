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

package android.car.vms;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.CarManagerBase;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.concurrent.Executor;

/**
 * API implementation for use by Vehicle Map Service subscribers.
 *
 * Supports a single client callback that can subscribe and unsubscribe to different data layers.
 * {@link #setVmsSubscriberClientCallback} must be called before any subscription operations.
 *
 * @hide
 */
@SystemApi
public final class VmsSubscriberManager implements CarManagerBase {
    private static final boolean DBG = true;
    private static final String TAG = "VmsSubscriberManager";

    private final IVmsSubscriberService mVmsSubscriberService;
    private final IVmsSubscriberClient mSubscriberManagerClient;
    private final Object mClientCallbackLock = new Object();
    @GuardedBy("mClientCallbackLock")
    private VmsSubscriberClientCallback mClientCallback;
    @GuardedBy("mClientCallbackLock")
    private Executor mExecutor;

    /**
     * Callback interface for Vehicle Map Service subscribers.
     */
    public interface VmsSubscriberClientCallback {
        /**
         * Called when a data packet is received.
         *
         * @param layer   subscribed layer that packet was received for
         * @param payload data packet that was received
         */
        void onVmsMessageReceived(@NonNull VmsLayer layer, byte[] payload);

        /**
         * Called when set of available data layers changes.
         *
         * @param availableLayers set of available data layers
         */
        void onLayersAvailabilityChanged(@NonNull VmsAvailableLayers availableLayers);
    }

    /**
     * Hidden constructor - can only be used internally.
     *
     * @hide
     */
    public VmsSubscriberManager(IBinder service) {
        mVmsSubscriberService = IVmsSubscriberService.Stub.asInterface(service);
        mSubscriberManagerClient = new IVmsSubscriberClient.Stub() {
            @Override
            public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
                Executor executor;
                synchronized (mClientCallbackLock) {
                    executor = mExecutor;
                }
                if (executor == null) {
                    if (DBG) {
                        Log.d(TAG, "Executor is null in onVmsMessageReceived");
                    }
                    return;
                }
                Binder.clearCallingIdentity();
                executor.execute(() -> {
                    dispatchOnReceiveMessage(layer, payload);
                });
            }

            @Override
            public void onLayersAvailabilityChanged(VmsAvailableLayers availableLayers) {
                Executor executor;
                synchronized (mClientCallbackLock) {
                    executor = mExecutor;
                }
                if (executor == null) {
                    if (DBG) {
                        Log.d(TAG, "Executor is null in onLayersAvailabilityChanged");
                    }
                    return;
                }
                Binder.clearCallingIdentity();
                executor.execute(() -> {
                    dispatchOnAvailabilityChangeMessage(availableLayers);
                });
            }
        };
    }

    /**
     * Sets the subscriber client's callback, for receiving layer availability and data events.
     *
     * @param executor       {@link Executor} to handle the callbacks
     * @param clientCallback subscriber callback that will handle events
     * @throws IllegalStateException if the client callback was already set
     */
    public void setVmsSubscriberClientCallback(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull VmsSubscriberClientCallback clientCallback) {
        synchronized (mClientCallbackLock) {
            if (mClientCallback != null) {
                throw new IllegalStateException("Client callback is already configured.");
            }
            mClientCallback = Preconditions.checkNotNull(clientCallback,
                    "clientCallback cannot be null");
            mExecutor = Preconditions.checkNotNull(executor, "executor cannot be null");
        }
        try {
            mVmsSubscriberService.addVmsSubscriberToNotifications(mSubscriberManagerClient);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }


    /**
     * Clears the subscriber client's callback.
     */
    public void clearVmsSubscriberClientCallback() {
        synchronized (mClientCallbackLock) {
            if (mExecutor == null) return;
        }
        try {
            mVmsSubscriberService.removeVmsSubscriberToNotifications(mSubscriberManagerClient);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            synchronized (mClientCallbackLock) {
                mClientCallback = null;
                mExecutor = null;
            }
        }
    }

    /**
     * Gets a publisher's self-reported description information.
     *
     * @param publisherId publisher ID to retrieve information for
     * @return serialized publisher information, in a vendor-specific format
     */
    @NonNull
    public byte[] getPublisherInfo(int publisherId) {
        try {
            return mVmsSubscriberService.getPublisherInfo(publisherId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets all layers available for subscription.
     *
     * @return available layers
     */
    @NonNull
    public VmsAvailableLayers getAvailableLayers() {
        try {
            return mVmsSubscriberService.getAvailableLayers();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Subscribes to data packets for a specific layer.
     *
     * @param layer layer to subscribe to
     * @throws IllegalStateException if the client callback was not set via
     *                               {@link #setVmsSubscriberClientCallback}.
     */
    public void subscribe(@NonNull VmsLayer layer) {
        verifySubscriptionIsAllowed();
        try {
            mVmsSubscriberService.addVmsSubscriber(mSubscriberManagerClient, layer);
            VmsOperationRecorder.get().subscribe(layer);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Subscribes to data packets for a specific layer from a specific publisher.
     *
     * @param layer       layer to subscribe to
     * @param publisherId a publisher of the layer
     * @throws IllegalStateException if the client callback was not set via
     *                               {@link #setVmsSubscriberClientCallback}.
     */
    public void subscribe(@NonNull VmsLayer layer, int publisherId) {
        verifySubscriptionIsAllowed();
        try {
            mVmsSubscriberService.addVmsSubscriberToPublisher(
                    mSubscriberManagerClient, layer, publisherId);
            VmsOperationRecorder.get().subscribe(layer, publisherId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Start monitoring all messages for all layers, regardless of subscriptions.
     */
    public void startMonitoring() {
        verifySubscriptionIsAllowed();
        try {
            mVmsSubscriberService.addVmsSubscriberPassive(mSubscriberManagerClient);
            VmsOperationRecorder.get().startMonitoring();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unsubscribes from data packets for a specific layer.
     *
     * @param layer layer to unsubscribe from
     * @throws IllegalStateException if the client callback was not set via
     *                               {@link #setVmsSubscriberClientCallback}.
     */
    public void unsubscribe(@NonNull VmsLayer layer) {
        verifySubscriptionIsAllowed();
        try {
            mVmsSubscriberService.removeVmsSubscriber(mSubscriberManagerClient, layer);
            VmsOperationRecorder.get().unsubscribe(layer);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unsubscribes from data packets for a specific layer from a specific publisher.
     *
     * @param layer       layer to unsubscribe from
     * @param publisherId a publisher of the layer
     * @throws IllegalStateException if the client callback was not set via
     *                               {@link #setVmsSubscriberClientCallback}.
     */
    public void unsubscribe(@NonNull VmsLayer layer, int publisherId) {
        try {
            mVmsSubscriberService.removeVmsSubscriberToPublisher(
                    mSubscriberManagerClient, layer, publisherId);
            VmsOperationRecorder.get().unsubscribe(layer, publisherId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stop monitoring. Only receive messages for layers which have been subscribed to."
     */
    public void stopMonitoring() {
        try {
            mVmsSubscriberService.removeVmsSubscriberPassive(mSubscriberManagerClient);
            VmsOperationRecorder.get().stopMonitoring();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void dispatchOnReceiveMessage(VmsLayer layer, byte[] payload) {
        VmsSubscriberClientCallback clientCallback = getClientCallbackThreadSafe();
        if (clientCallback == null) {
            Log.e(TAG, "Cannot dispatch received message.");
            return;
        }
        clientCallback.onVmsMessageReceived(layer, payload);
    }

    private void dispatchOnAvailabilityChangeMessage(VmsAvailableLayers availableLayers) {
        VmsSubscriberClientCallback clientCallback = getClientCallbackThreadSafe();
        if (clientCallback == null) {
            Log.e(TAG, "Cannot dispatch availability change message.");
            return;
        }
        clientCallback.onLayersAvailabilityChanged(availableLayers);
    }

    private VmsSubscriberClientCallback getClientCallbackThreadSafe() {
        VmsSubscriberClientCallback clientCallback;
        synchronized (mClientCallbackLock) {
            clientCallback = mClientCallback;
        }
        if (clientCallback == null) {
            Log.e(TAG, "client callback not set.");
        }
        return clientCallback;
    }

    /*
     * Verifies that the subscriber is in a state where it is allowed to subscribe.
     */
    private void verifySubscriptionIsAllowed() {
        VmsSubscriberClientCallback clientCallback = getClientCallbackThreadSafe();
        if (clientCallback == null) {
            throw new IllegalStateException("Cannot subscribe.");
        }
    }

    /**
     * @hide
     */
    @Override
    public void onCarDisconnected() {
    }
}
