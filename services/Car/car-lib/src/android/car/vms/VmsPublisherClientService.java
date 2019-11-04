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


import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.ref.WeakReference;

/**
 * API implementation of a Vehicle Map Service publisher client.
 *
 * All publisher clients must inherit from this class and export it as a service, and the service
 * be added to either the {@code vmsPublisherSystemClients} or {@code vmsPublisherUserClients}
 * arrays in the Car service configuration, depending on which user the client will run as.
 *
 * The {@link com.android.car.VmsPublisherService} will then bind to this service, with the
 * {@link #onVmsPublisherServiceReady()} callback notifying the client implementation when the
 * connection is established and publisher operations can be called.
 *
 * Publishers must also register a publisher ID by calling {@link #getPublisherId(byte[])}.
 *
 * @hide
 */
@SystemApi
public abstract class VmsPublisherClientService extends Service {
    private static final boolean DBG = true;
    private static final String TAG = "VmsPublisherClientService";

    private final Object mLock = new Object();

    private Handler mHandler = new VmsEventHandler(this);
    private final VmsPublisherClientBinder mVmsPublisherClient = new VmsPublisherClientBinder(this);
    private volatile IVmsPublisherService mVmsPublisherService = null;
    @GuardedBy("mLock")
    private IBinder mToken = null;

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) {
            Log.d(TAG, "onBind, intent: " + intent);
        }
        return mVmsPublisherClient.asBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DBG) {
            Log.d(TAG, "onUnbind, intent: " + intent);
        }
        stopSelf();
        return super.onUnbind(intent);
    }

    private void setToken(IBinder token) {
        synchronized (mLock) {
            mToken = token;
        }
    }

    /**
     * Notifies the client that publisher services are ready.
     */
    protected abstract void onVmsPublisherServiceReady();

    /**
     * Notifies the client of changes in layer subscriptions.
     *
     * @param subscriptionState state of layer subscriptions
     */
    public abstract void onVmsSubscriptionChange(@NonNull VmsSubscriptionState subscriptionState);

    /**
     * Publishes a data packet to subscribers.
     *
     * Publishers must only publish packets for the layers that they have made offerings for.
     *
     * @param layer       layer to publish to
     * @param publisherId ID of the publisher publishing the message
     * @param payload     data packet to be sent
     * @throws IllegalStateException if publisher services are not available
     */
    public final void publish(@NonNull VmsLayer layer, int publisherId, byte[] payload) {
        Preconditions.checkNotNull(layer, "layer cannot be null");
        if (DBG) {
            Log.d(TAG, "Publishing for layer : " + layer);
        }

        IBinder token = getTokenForPublisherServiceThreadSafe();

        try {
            mVmsPublisherService.publish(token, layer, publisherId, payload);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the layers offered by a specific publisher.
     *
     * @param offering layers being offered for subscription by the publisher
     * @throws IllegalStateException if publisher services are not available
     */
    public final void setLayersOffering(@NonNull VmsLayersOffering offering) {
        Preconditions.checkNotNull(offering, "offering cannot be null");
        if (DBG) {
            Log.d(TAG, "Setting layers offering : " + offering);
        }

        IBinder token = getTokenForPublisherServiceThreadSafe();

        try {
            mVmsPublisherService.setLayersOffering(token, offering);
            VmsOperationRecorder.get().setLayersOffering(offering);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private IBinder getTokenForPublisherServiceThreadSafe() {
        if (mVmsPublisherService == null) {
            throw new IllegalStateException("VmsPublisherService not set.");
        }

        IBinder token;
        synchronized (mLock) {
            token = mToken;
        }
        if (token == null) {
            throw new IllegalStateException("VmsPublisherService does not have a valid token.");
        }
        return token;
    }

    /**
     * Acquires a publisher ID for a serialized publisher description.
     *
     * Multiple calls to this method with the same information will return the same publisher ID.
     *
     * @param publisherInfo serialized publisher description information, in a vendor-specific
     *                      format
     * @return a publisher ID for the given publisher description
     * @throws IllegalStateException if publisher services are not available
     */
    public final int getPublisherId(byte[] publisherInfo) {
        if (mVmsPublisherService == null) {
            throw new IllegalStateException("VmsPublisherService not set.");
        }
        int publisherId;
        try {
            Log.i(TAG, "Getting publisher static ID");
            publisherId = mVmsPublisherService.getPublisherId(publisherInfo);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        VmsOperationRecorder.get().getPublisherId(publisherId);
        return publisherId;
    }

    /**
     * Gets the state of layer subscriptions.
     *
     * @return state of layer subscriptions
     * @throws IllegalStateException if publisher services are not available
     */
    public final VmsSubscriptionState getSubscriptions() {
        if (mVmsPublisherService == null) {
            throw new IllegalStateException("VmsPublisherService not set.");
        }
        try {
            return mVmsPublisherService.getSubscriptions();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void setVmsPublisherService(IVmsPublisherService service) {
        mVmsPublisherService = service;
        onVmsPublisherServiceReady();
    }

    /**
     * Implements the interface that the VMS service uses to communicate with this client.
     */
    private static class VmsPublisherClientBinder extends IVmsPublisherClient.Stub {
        private final WeakReference<VmsPublisherClientService> mVmsPublisherClientService;
        @GuardedBy("mSequenceLock")
        private long mSequence = -1;
        private final Object mSequenceLock = new Object();

        VmsPublisherClientBinder(VmsPublisherClientService vmsPublisherClientService) {
            mVmsPublisherClientService = new WeakReference<>(vmsPublisherClientService);
        }

        @Override
        public void setVmsPublisherService(IBinder token, IVmsPublisherService service) {
            assertSystemOrSelf();

            VmsPublisherClientService vmsPublisherClientService = mVmsPublisherClientService.get();
            if (vmsPublisherClientService == null) return;
            if (DBG) {
                Log.d(TAG, "setting VmsPublisherService.");
            }
            Handler handler = vmsPublisherClientService.mHandler;
            handler.sendMessage(
                    handler.obtainMessage(VmsEventHandler.SET_SERVICE_CALLBACK, service));
            vmsPublisherClientService.setToken(token);
        }

        @Override
        public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState) {
            assertSystemOrSelf();

            VmsPublisherClientService vmsPublisherClientService = mVmsPublisherClientService.get();
            if (vmsPublisherClientService == null) return;
            if (DBG) {
                Log.d(TAG, "subscription event: " + subscriptionState);
            }
            synchronized (mSequenceLock) {
                if (subscriptionState.getSequenceNumber() <= mSequence) {
                    Log.w(TAG, "Sequence out of order. Current sequence = " + mSequence
                            + "; expected new sequence = " + subscriptionState.getSequenceNumber());
                    // Do not propagate old notifications.
                    return;
                } else {
                    mSequence = subscriptionState.getSequenceNumber();
                }
            }
            Handler handler = vmsPublisherClientService.mHandler;
            handler.sendMessage(
                    handler.obtainMessage(VmsEventHandler.ON_SUBSCRIPTION_CHANGE_EVENT,
                            subscriptionState));
        }

        private void assertSystemOrSelf() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (DBG) Log.d(TAG, "Skipping system user check");
                return;
            }

            if (!(Binder.getCallingUid() == Process.SYSTEM_UID
                    || Binder.getCallingPid() == Process.myPid())) {
                throw new SecurityException("Caller must be system user or same process");
            }
        }
    }

    /**
     * Receives events from the binder thread and dispatches them.
     */
    private final static class VmsEventHandler extends Handler {
        /** Constants handled in the handler */
        private static final int ON_SUBSCRIPTION_CHANGE_EVENT = 0;
        private static final int SET_SERVICE_CALLBACK = 1;

        private final WeakReference<VmsPublisherClientService> mVmsPublisherClientService;

        VmsEventHandler(VmsPublisherClientService service) {
            super(Looper.getMainLooper());
            mVmsPublisherClientService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            VmsPublisherClientService service = mVmsPublisherClientService.get();
            if (service == null) return;
            switch (msg.what) {
                case ON_SUBSCRIPTION_CHANGE_EVENT:
                    VmsSubscriptionState subscriptionState = (VmsSubscriptionState) msg.obj;
                    service.onVmsSubscriptionChange(subscriptionState);
                    break;
                case SET_SERVICE_CALLBACK:
                    service.setVmsPublisherService((IVmsPublisherService) msg.obj);
                    break;
                default:
                    Log.e(TAG, "Event type not handled:  " + msg.what);
                    break;
            }
        }
    }
}
