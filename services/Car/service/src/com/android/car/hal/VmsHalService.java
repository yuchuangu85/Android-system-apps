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
package com.android.car.hal;

import static com.android.car.CarServiceUtils.toByteArray;

import static java.lang.Integer.toHexString;

import android.car.VehicleAreaType;
import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsPublisherService;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.IVmsSubscriberService;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VmsBaseMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.hardware.automotive.vehicle.V2_0.VmsMessageWithLayerAndPublisherIdIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsMessageWithLayerIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsOfferingMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsPublisherInformationIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsStartSessionMessageIntegerValuesIndex;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.car.CarLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * VMS client implementation that proxies VmsPublisher/VmsSubscriber API calls to the Vehicle HAL
 * using HAL-specific message encodings.
 *
 * @see android.hardware.automotive.vehicle.V2_0
 */
public class VmsHalService extends HalServiceBase {
    private static final boolean DBG = true;
    private static final String TAG = "VmsHalService";
    private static final int HAL_PROPERTY_ID = VehicleProperty.VEHICLE_MAP_SERVICE;
    private static final int NUM_INTEGERS_IN_VMS_LAYER = 3;
    private static final int UNKNOWN_CLIENT_ID = -1;

    private final VehicleHal mVehicleHal;
    private final int mCoreId;
    private final MessageQueue mMessageQueue;
    private volatile boolean mIsSupported = false;

    private IVmsPublisherService mPublisherService;
    private Consumer<IBinder> mPublisherOnHalConnected;
    private Runnable mPublisherOnHalDisconnected;
    private IBinder mPublisherToken;

    private IVmsSubscriberService mSubscriberService;
    private Consumer<IVmsSubscriberClient> mSuscriberOnHalDisconnected;

    private int mSubscriptionStateSequence = -1;
    private int mAvailableLayersSequence = -1;

    private final IVmsPublisherClient.Stub mPublisherClient = new IVmsPublisherClient.Stub() {
        @Override
        public void setVmsPublisherService(IBinder token, IVmsPublisherService service) {
            mPublisherToken = token;
            mPublisherService = service;
        }

        @Override
        public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState) {
            if (DBG) Log.d(TAG, "Handling a subscription state change");
            // Drop out-of-order notifications
            if (subscriptionState.getSequenceNumber() <= mSubscriptionStateSequence) {
                Log.w(TAG,
                        String.format("Out of order subscription state received: %d (expecting %d)",
                                subscriptionState.getSequenceNumber(),
                                mSubscriptionStateSequence + 1));
                return;
            }
            mSubscriptionStateSequence = subscriptionState.getSequenceNumber();
            mMessageQueue.enqueue(VmsMessageType.SUBSCRIPTIONS_CHANGE,
                    createSubscriptionStateMessage(VmsMessageType.SUBSCRIPTIONS_CHANGE,
                            subscriptionState));
        }
    };

    private final IVmsSubscriberClient.Stub mSubscriberClient = new IVmsSubscriberClient.Stub() {
        @Override
        public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
            if (DBG) Log.d(TAG, "Handling a data message for Layer: " + layer);
            mMessageQueue.enqueue(VmsMessageType.DATA, createDataMessage(layer, payload));
        }

        @Override
        public void onLayersAvailabilityChanged(VmsAvailableLayers availableLayers) {
            if (DBG) Log.d(TAG, "Handling a layer availability change");
            // Drop out-of-order notifications
            if (availableLayers.getSequence() <= mAvailableLayersSequence) {
                Log.w(TAG,
                        String.format("Out of order layer availability received: %d (expecting %d)",
                                availableLayers.getSequence(),
                                mAvailableLayersSequence + 1));
                return;
            }
            mAvailableLayersSequence = availableLayers.getSequence();
            mMessageQueue.enqueue(VmsMessageType.AVAILABILITY_CHANGE,
                    createAvailableLayersMessage(VmsMessageType.AVAILABILITY_CHANGE,
                            availableLayers));
        }
    };

    private class MessageQueue implements Handler.Callback {
        private final Set<Integer> mSupportedMessageTypes = new ArraySet<>(Arrays.asList(
                VmsMessageType.DATA,
                VmsMessageType.START_SESSION,
                VmsMessageType.AVAILABILITY_CHANGE,
                VmsMessageType.SUBSCRIPTIONS_CHANGE
        ));
        private HandlerThread mHandlerThread;
        private Handler mHandler;

        synchronized void init() {
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper(), this);
        }

        synchronized void release() {
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
            }
        }

        synchronized void enqueue(int messageType, Object message) {
            if (mSupportedMessageTypes.contains(messageType)) {
                Message.obtain(mHandler, messageType, message).sendToTarget();
            } else {
                Log.e(TAG, "Unexpected message type: " + VmsMessageType.toString(messageType));
            }
        }

        synchronized void clear() {
            mSupportedMessageTypes.forEach(mHandler::removeMessages);
        }

        @Override
        public boolean handleMessage(Message msg) {
            int messageType = msg.what;
            VehiclePropValue vehicleProp = (VehiclePropValue) msg.obj;
            if (DBG) Log.d(TAG, "Sending " + VmsMessageType.toString(messageType) + " message");
            try {
                setPropertyValue(vehicleProp);
            } catch (RemoteException e) {
                Log.e(TAG, "While sending " + VmsMessageType.toString(messageType));
            }
            return true;
        }
    }

    /**
     * Constructor used by {@link VehicleHal}
     */
    VmsHalService(VehicleHal vehicleHal) {
        this(vehicleHal, SystemClock::uptimeMillis);
    }

    @VisibleForTesting
    VmsHalService(VehicleHal vehicleHal, Supplier<Long> getCoreId) {
        mVehicleHal = vehicleHal;
        mCoreId = (int) (getCoreId.get() % Integer.MAX_VALUE);
        mMessageQueue = new MessageQueue();
    }

    /**
     * Retrieves the callback message handler for use by unit tests.
     */
    @VisibleForTesting
    Handler getHandler() {
        return mMessageQueue.mHandler;
    }

    /**
     * Gets the {@link IVmsPublisherClient} implementation for the HAL's publisher callback.
     */
    public void setPublisherConnectionCallbacks(Consumer<IBinder> onHalConnected,
            Runnable onHalDisconnected) {
        mPublisherOnHalConnected = onHalConnected;
        mPublisherOnHalDisconnected = onHalDisconnected;
    }

    /**
     * Sets a reference to the {@link IVmsSubscriberService} implementation for use by the HAL.
     */
    public void setVmsSubscriberService(IVmsSubscriberService service,
            Consumer<IVmsSubscriberClient> onHalDisconnected) {
        mSubscriberService = service;
        mSuscriberOnHalDisconnected = onHalDisconnected;
    }

    @Override
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        for (VehiclePropConfig p : allProperties) {
            if (p.prop == HAL_PROPERTY_ID) {
                mIsSupported = true;
                return Collections.singleton(p);
            }
        }
        return Collections.emptySet();
    }

    @Override
    public void init() {
        if (mIsSupported) {
            if (DBG) Log.d(TAG, "Initializing VmsHalService VHAL property");
            mVehicleHal.subscribeProperty(this, HAL_PROPERTY_ID);
        } else {
            if (DBG) Log.d(TAG, "VmsHalService VHAL property not supported");
            return; // Do not continue initialization
        }

        mMessageQueue.init();
        mMessageQueue.enqueue(VmsMessageType.START_SESSION,
                createStartSessionMessage(mCoreId, UNKNOWN_CLIENT_ID));
    }

    @Override
    public void release() {
        mMessageQueue.release();
        mSubscriptionStateSequence = -1;
        mAvailableLayersSequence = -1;

        if (mIsSupported) {
            if (DBG) Log.d(TAG, "Releasing VmsHalService VHAL property");
            mVehicleHal.unsubscribeProperty(this, HAL_PROPERTY_ID);
        } else {
            return;
        }

        if (mSubscriberService != null) {
            try {
                mSubscriberService.removeVmsSubscriberToNotifications(mSubscriberClient);
            } catch (RemoteException e) {
                Log.e(TAG, "While removing subscriber callback", e);
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*VMS HAL*");

        writer.println("VmsProperty: " + (mIsSupported ? "supported" : "unsupported"));
        writer.println("VmsPublisherService: "
                + (mPublisherService != null ? "registered " : "unregistered"));
        writer.println("mSubscriptionStateSequence: " + mSubscriptionStateSequence);

        writer.println("VmsSubscriberService: "
                + (mSubscriberService != null ? "registered" : "unregistered"));
        writer.println("mAvailableLayersSequence: " + mAvailableLayersSequence);
    }

    /**
     * Consumes/produces HAL messages.
     *
     * The format of these messages is defined in:
     * hardware/interfaces/automotive/vehicle/2.0/types.hal
     */
    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        if (DBG) Log.d(TAG, "Handling a VMS property change");
        for (VehiclePropValue v : values) {
            ArrayList<Integer> vec = v.value.int32Values;
            int messageType = vec.get(VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE);

            if (DBG) Log.d(TAG, "Received " + VmsMessageType.toString(messageType) + " message");
            try {
                switch (messageType) {
                    case VmsMessageType.DATA:
                        handleDataEvent(vec, toByteArray(v.value.bytes));
                        break;
                    case VmsMessageType.SUBSCRIBE:
                        handleSubscribeEvent(vec);
                        break;
                    case VmsMessageType.UNSUBSCRIBE:
                        handleUnsubscribeEvent(vec);
                        break;
                    case VmsMessageType.SUBSCRIBE_TO_PUBLISHER:
                        handleSubscribeToPublisherEvent(vec);
                        break;
                    case VmsMessageType.UNSUBSCRIBE_TO_PUBLISHER:
                        handleUnsubscribeFromPublisherEvent(vec);
                        break;
                    case VmsMessageType.PUBLISHER_ID_REQUEST:
                        handlePublisherIdRequest(toByteArray(v.value.bytes));
                        break;
                    case VmsMessageType.PUBLISHER_INFORMATION_REQUEST:
                        handlePublisherInfoRequest(vec);
                    case VmsMessageType.OFFERING:
                        handleOfferingEvent(vec);
                        break;
                    case VmsMessageType.AVAILABILITY_REQUEST:
                        handleAvailabilityRequestEvent();
                        break;
                    case VmsMessageType.SUBSCRIPTIONS_REQUEST:
                        handleSubscriptionsRequestEvent();
                        break;
                    case VmsMessageType.START_SESSION:
                        handleStartSessionEvent(vec);
                        break;
                    default:
                        Log.e(TAG, "Unexpected message type: " + messageType);
                }
            } catch (IndexOutOfBoundsException | RemoteException e) {
                Log.e(TAG, "While handling: " + messageType, e);
            }
        }
    }

    /**
     * SESSION_START message format:
     * <ul>
     * <li>Message type
     * <li>Core ID
     * <li>Client ID
     * </ul>
     */
    private void handleStartSessionEvent(List<Integer> message) {
        int coreId = message.get(VmsStartSessionMessageIntegerValuesIndex.SERVICE_ID);
        int clientId = message.get(VmsStartSessionMessageIntegerValuesIndex.CLIENT_ID);
        if (DBG) {
            Log.d(TAG,
                    "Handling a session start event with coreId: " + coreId + " client: "
                            + clientId);
        }

        if (coreId != mCoreId) {
            if (mPublisherOnHalDisconnected != null) {
                mPublisherOnHalDisconnected.run();
            } else {
                Log.w(TAG, "Publisher disconnect callback not registered");
            }
            if (mSuscriberOnHalDisconnected != null) {
                mSuscriberOnHalDisconnected.accept(mSubscriberClient);
            } else {
                Log.w(TAG, "Subscriber disconnect callback not registered");
            }

            // Drop all queued messages and client state
            mMessageQueue.clear();
            mSubscriptionStateSequence = -1;
            mAvailableLayersSequence = -1;

            // Enqueue an acknowledgement message
            mMessageQueue.enqueue(VmsMessageType.START_SESSION,
                    createStartSessionMessage(mCoreId, clientId));
        }

        // Notify client manager of connection
        if (mPublisherOnHalConnected != null) {
            mPublisherOnHalConnected.accept(mPublisherClient);
        } else {
            Log.w(TAG, "Publisher connect callback not registered");
        }

        // Notify subscriber service of connection
        if (mSubscriberService != null) {
            try {
                mSubscriberService.addVmsSubscriberToNotifications(mSubscriberClient);
            } catch (RemoteException e) {
                Log.e(TAG, "While adding subscriber callback", e);
            }

            // Publish layer availability to HAL clients (this triggers HAL client initialization)
            try {
                mSubscriberClient.onLayersAvailabilityChanged(
                        mSubscriberService.getAvailableLayers());
            } catch (RemoteException e) {
                Log.e(TAG, "While publishing layer availability", e);
            }
        } else {
            Log.w(TAG, "Subscriber connect callback not registered");
        }
    }

    /**
     * DATA message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * <li>Payload
     * </ul>
     */
    private void handleDataEvent(List<Integer> message, byte[] payload)
            throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Log.d(TAG,
                    "Handling a data event for Layer: " + vmsLayer + " Publisher: " + publisherId);
        }
        mPublisherService.publish(mPublisherToken, vmsLayer, publisherId, payload);
    }

    /**
     * SUBSCRIBE message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     */
    private void handleSubscribeEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        if (DBG) Log.d(TAG, "Handling a subscribe event for Layer: " + vmsLayer);
        mSubscriberService.addVmsSubscriber(mSubscriberClient, vmsLayer);
    }

    /**
     * SUBSCRIBE_TO_PUBLISHER message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * </ul>
     */
    private void handleSubscribeToPublisherEvent(List<Integer> message)
            throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Log.d(TAG,
                    "Handling a subscribe event for Layer: " + vmsLayer + " Publisher: "
                            + publisherId);
        }
        mSubscriberService.addVmsSubscriberToPublisher(mSubscriberClient, vmsLayer, publisherId);
    }

    /**
     * UNSUBSCRIBE message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     */
    private void handleUnsubscribeEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        if (DBG) Log.d(TAG, "Handling an unsubscribe event for Layer: " + vmsLayer);
        mSubscriberService.removeVmsSubscriber(mSubscriberClient, vmsLayer);
    }

    /**
     * UNSUBSCRIBE_TO_PUBLISHER message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * </ul>
     */
    private void handleUnsubscribeFromPublisherEvent(List<Integer> message)
            throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Log.d(TAG, "Handling an unsubscribe event for Layer: " + vmsLayer + " Publisher: "
                    + publisherId);
        }
        mSubscriberService.removeVmsSubscriberToPublisher(mSubscriberClient, vmsLayer, publisherId);
    }

    /**
     * PUBLISHER_ID_REQUEST message format:
     * <ul>
     * <li>Message type
     * <li>Publisher info (bytes)
     * </ul>
     *
     * PUBLISHER_ID_RESPONSE message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * </ul>
     */
    private void handlePublisherIdRequest(byte[] payload)
            throws RemoteException {
        if (DBG) Log.d(TAG, "Handling a publisher id request event");

        VehiclePropValue vehicleProp = createVmsMessage(VmsMessageType.PUBLISHER_ID_RESPONSE);
        // Publisher ID
        vehicleProp.value.int32Values.add(mPublisherService.getPublisherId(payload));

        setPropertyValue(vehicleProp);
    }


    /**
     * PUBLISHER_INFORMATION_REQUEST message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * </ul>
     *
     * PUBLISHER_INFORMATION_RESPONSE message format:
     * <ul>
     * <li>Message type
     * <li>Publisher info (bytes)
     * </ul>
     */
    private void handlePublisherInfoRequest(List<Integer> message)
            throws RemoteException {
        if (DBG) Log.d(TAG, "Handling a publisher info request event");
        int publisherId = message.get(VmsPublisherInformationIntegerValuesIndex.PUBLISHER_ID);

        VehiclePropValue vehicleProp =
                createVmsMessage(VmsMessageType.PUBLISHER_INFORMATION_RESPONSE);
        // Publisher Info
        appendBytes(vehicleProp.value.bytes, mSubscriberService.getPublisherInfo(publisherId));

        setPropertyValue(vehicleProp);
    }

    /**
     * OFFERING message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * <li>Number of offerings.
     * <li>Offerings (x number of offerings)
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Number of layer dependencies.
     * <li>Layer dependencies (x number of layer dependencies)
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     * </ul>
     * </ul>
     */
    private void handleOfferingEvent(List<Integer> message) throws RemoteException {
        // Publisher ID for OFFERING is stored at a different index than in other message types
        int publisherId = message.get(VmsOfferingMessageIntegerValuesIndex.PUBLISHER_ID);
        int numLayerDependencies =
                message.get(
                        VmsOfferingMessageIntegerValuesIndex.NUMBER_OF_OFFERS);
        if (DBG) {
            Log.d(TAG, "Handling an offering event of " + numLayerDependencies
                    + " layers for Publisher: " + publisherId);
        }

        Set<VmsLayerDependency> offeredLayers = new ArraySet<>(numLayerDependencies);
        int idx = VmsOfferingMessageIntegerValuesIndex.OFFERING_START;
        for (int i = 0; i < numLayerDependencies; i++) {
            VmsLayer offeredLayer = parseVmsLayerAtIndex(message, idx);
            idx += NUM_INTEGERS_IN_VMS_LAYER;

            int numDependenciesForLayer = message.get(idx++);
            if (numDependenciesForLayer == 0) {
                offeredLayers.add(new VmsLayerDependency(offeredLayer));
            } else {
                Set<VmsLayer> dependencies = new HashSet<>();

                for (int j = 0; j < numDependenciesForLayer; j++) {
                    VmsLayer dependantLayer = parseVmsLayerAtIndex(message, idx);
                    idx += NUM_INTEGERS_IN_VMS_LAYER;
                    dependencies.add(dependantLayer);
                }
                offeredLayers.add(new VmsLayerDependency(offeredLayer, dependencies));
            }
        }

        VmsLayersOffering offering = new VmsLayersOffering(offeredLayers, publisherId);
        VmsOperationRecorder.get().setHalPublisherLayersOffering(offering);
        mPublisherService.setLayersOffering(mPublisherToken, offering);
    }

    /**
     * AVAILABILITY_REQUEST message format:
     * <ul>
     * <li>Message type
     * </ul>
     */
    private void handleAvailabilityRequestEvent() throws RemoteException {
        setPropertyValue(
                createAvailableLayersMessage(VmsMessageType.AVAILABILITY_RESPONSE,
                        mSubscriberService.getAvailableLayers()));
    }

    /**
     * SUBSCRIPTION_REQUEST message format:
     * <ul>
     * <li>Message type
     * </ul>
     */
    private void handleSubscriptionsRequestEvent() throws RemoteException {
        setPropertyValue(
                createSubscriptionStateMessage(VmsMessageType.SUBSCRIPTIONS_RESPONSE,
                        mPublisherService.getSubscriptions()));
    }

    private void setPropertyValue(VehiclePropValue vehicleProp) throws RemoteException {
        int messageType = vehicleProp.value.int32Values.get(
                VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE);

        if (!mIsSupported) {
            Log.w(TAG, "HAL unsupported while attempting to send "
                    + VmsMessageType.toString(messageType));
            return;
        }

        try {
            mVehicleHal.set(vehicleProp);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_PROPERTY,
                    "set, property not ready 0x" + toHexString(HAL_PROPERTY_ID));
            throw new RemoteException(
                    "Timeout while sending " + VmsMessageType.toString(messageType));
        }
    }

    /**
     * Creates a SESSION_START type {@link VehiclePropValue}.
     *
     * SESSION_START message format:
     * <ul>
     * <li>Message type
     * <li>Core ID
     * <li>Client ID
     * </ul>
     */
    private static VehiclePropValue createStartSessionMessage(int coreId, int clientId) {
        // Message type + layer
        VehiclePropValue vehicleProp = createVmsMessage(VmsMessageType.START_SESSION);
        List<Integer> message = vehicleProp.value.int32Values;

        // Core ID
        message.add(coreId);

        // Client ID
        message.add(clientId);

        return vehicleProp;
    }

    /**
     * Creates a DATA type {@link VehiclePropValue}.
     *
     * DATA message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * <li>Payload
     * </ul>
     *
     * @param layer Layer for which message was published.
     */
    private static VehiclePropValue createDataMessage(VmsLayer layer, byte[] payload) {
        // Message type + layer
        VehiclePropValue vehicleProp = createVmsMessage(VmsMessageType.DATA);
        appendLayer(vehicleProp.value.int32Values, layer);
        List<Integer> message = vehicleProp.value.int32Values;

        // Publisher ID
        // TODO(b/124130256): Set publisher ID of data message
        message.add(0);

        // Payload
        appendBytes(vehicleProp.value.bytes, payload);
        return vehicleProp;
    }

    /**
     * Creates a SUBSCRIPTION_CHANGE or SUBSCRIPTION_RESPONSE type {@link VehiclePropValue}.
     *
     * Both message types have the same format:
     * <ul>
     * <li>Message type
     * <li>Sequence number
     * <li>Number of layers
     * <li>Number of associated layers
     * <li>Layers (x number of layers) (see {@link #appendLayer})
     * <li>Associated layers (x number of associated layers) (see {@link #appendAssociatedLayer})
     * </ul>
     *
     * @param messageType       Either SUBSCRIPTIONS_CHANGE or SUBSCRIPTIONS_RESPONSE.
     * @param subscriptionState The subscription state to encode in the message.
     */
    private static VehiclePropValue createSubscriptionStateMessage(int messageType,
            VmsSubscriptionState subscriptionState) {
        // Message type
        VehiclePropValue vehicleProp = createVmsMessage(messageType);
        List<Integer> message = vehicleProp.value.int32Values;

        // Sequence number
        message.add(subscriptionState.getSequenceNumber());

        Set<VmsLayer> layers = subscriptionState.getLayers();
        Set<VmsAssociatedLayer> associatedLayers = subscriptionState.getAssociatedLayers();

        // Number of layers
        message.add(layers.size());
        // Number of associated layers
        message.add(associatedLayers.size());

        // Layers
        for (VmsLayer layer : layers) {
            appendLayer(message, layer);
        }

        // Associated layers
        for (VmsAssociatedLayer layer : associatedLayers) {
            appendAssociatedLayer(message, layer);
        }
        return vehicleProp;
    }

    /**
     * Creates an AVAILABILITY_CHANGE or AVAILABILITY_RESPONSE type {@link VehiclePropValue}.
     *
     * Both message types have the same format:
     * <ul>
     * <li>Message type
     * <li>Sequence number.
     * <li>Number of associated layers.
     * <li>Associated layers (x number of associated layers) (see {@link #appendAssociatedLayer})
     * </ul>
     *
     * @param messageType     Either AVAILABILITY_CHANGE or AVAILABILITY_RESPONSE.
     * @param availableLayers The available layers to encode in the message.
     */
    private static VehiclePropValue createAvailableLayersMessage(int messageType,
            VmsAvailableLayers availableLayers) {
        // Message type
        VehiclePropValue vehicleProp = createVmsMessage(messageType);
        List<Integer> message = vehicleProp.value.int32Values;

        // Sequence number
        message.add(availableLayers.getSequence());

        // Number of associated layers
        message.add(availableLayers.getAssociatedLayers().size());

        // Associated layers
        for (VmsAssociatedLayer layer : availableLayers.getAssociatedLayers()) {
            appendAssociatedLayer(message, layer);
        }
        return vehicleProp;
    }

    /**
     * Creates a base {@link VehiclePropValue} of the requested message type, with no message fields
     * populated.
     *
     * @param messageType Type of message, from {@link VmsMessageType}
     */
    private static VehiclePropValue createVmsMessage(int messageType) {
        VehiclePropValue vehicleProp = new VehiclePropValue();
        vehicleProp.prop = HAL_PROPERTY_ID;
        vehicleProp.areaId = VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
        vehicleProp.value.int32Values.add(messageType);
        return vehicleProp;
    }

    /**
     * Appends a {@link VmsLayer} to an encoded VMS message.
     *
     * Layer format:
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     *
     * @param message Message to append to.
     * @param layer   Layer to append.
     */
    private static void appendLayer(List<Integer> message, VmsLayer layer) {
        message.add(layer.getType());
        message.add(layer.getSubtype());
        message.add(layer.getVersion());
    }

    /**
     * Appends a {@link VmsAssociatedLayer} to an encoded VMS message.
     *
     * AssociatedLayer format:
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Number of publishers
     * <li>Publisher ID (x number of publishers)
     * </ul>
     *
     * @param message Message to append to.
     * @param layer   Layer to append.
     */
    private static void appendAssociatedLayer(List<Integer> message, VmsAssociatedLayer layer) {
        message.add(layer.getVmsLayer().getType());
        message.add(layer.getVmsLayer().getSubtype());
        message.add(layer.getVmsLayer().getVersion());
        message.add(layer.getPublisherIds().size());
        message.addAll(layer.getPublisherIds());
    }

    private static void appendBytes(ArrayList<Byte> dst, byte[] src) {
        dst.ensureCapacity(src.length);
        for (byte b : src) {
            dst.add(b);
        }
    }

    private static VmsLayer parseVmsLayerFromMessage(List<Integer> message) {
        return parseVmsLayerAtIndex(message,
                VmsMessageWithLayerIntegerValuesIndex.LAYER_TYPE);
    }

    private static VmsLayer parseVmsLayerAtIndex(List<Integer> message, int index) {
        List<Integer> layerValues = message.subList(index, index + NUM_INTEGERS_IN_VMS_LAYER);
        return new VmsLayer(layerValues.get(0), layerValues.get(1), layerValues.get(2));
    }

    private static int parsePublisherIdFromMessage(List<Integer> message) {
        return message.get(VmsMessageWithLayerAndPublisherIdIntegerValuesIndex.PUBLISHER_ID);
    }
}
