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

package android.car.testapi;

import android.bluetooth.BluetoothDevice;
import android.car.CarProjectionManager;
import android.car.CarProjectionManager.ProjectionAccessPointCallback;
import android.car.ICarProjection;
import android.car.ICarProjectionKeyEventHandler;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionOptions;
import android.car.projection.ProjectionStatus;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake implementation of {@link ICarProjection} interface.
 *
 * @hide
 */
class FakeCarProjectionService extends ICarProjection.Stub implements
        CarProjectionController {

    private final Context mContext;

    private WifiConfiguration mWifiConfiguration;
    private Messenger mApMessenger;
    private IBinder mApBinder;
    private List<ICarProjectionStatusListener> mStatusListeners = new ArrayList<>();
    private Map<IBinder, ProjectionStatus> mProjectionStatusMap = new HashMap<>();
    private ProjectionStatus mCurrentProjectionStatus = ProjectionStatus.builder(
            "", ProjectionStatus.PROJECTION_STATE_INACTIVE).build();
    private ProjectionOptions mProjectionOptions;
    private final Map<ICarProjectionKeyEventHandler, BitSet> mKeyEventListeners = new HashMap<>();

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    FakeCarProjectionService(Context context) {
        mContext = context;
        mProjectionOptions = ProjectionOptions.builder().build();
    }

    @Override
    public void registerProjectionRunner(Intent serviceIntent) throws RemoteException {
        mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void unregisterProjectionRunner(Intent serviceIntent) throws RemoteException {
        mContext.unbindService(mServiceConnection);
    }

    @Override
    public void registerKeyEventHandler(ICarProjectionKeyEventHandler callback, byte[] events) {
        mKeyEventListeners.put(callback, BitSet.valueOf(events));
    }

    @Override
    public void unregisterKeyEventHandler(ICarProjectionKeyEventHandler callback) {
        mKeyEventListeners.remove(callback);
    }

    @Override
    public void fireKeyEvent(int event) {
        for (Map.Entry<ICarProjectionKeyEventHandler, BitSet> entry :
                mKeyEventListeners.entrySet()) {
            if (entry.getValue().get(event)) {
                try {
                    entry.getKey().onKeyEvent(event);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    @Override
    public void startProjectionAccessPoint(Messenger messenger, IBinder binder)
            throws RemoteException {
        mApMessenger = messenger;
        mApBinder = binder;

        Message message = Message.obtain();
        if (mWifiConfiguration != null) {
            message.what = CarProjectionManager.PROJECTION_AP_STARTED;
            message.obj = mWifiConfiguration;
        } else {
            message.what = CarProjectionManager.PROJECTION_AP_FAILED;
            message.arg1 = ProjectionAccessPointCallback.ERROR_GENERIC;
        }
        messenger.send(message);
    }

    @Override
    public void stopProjectionAccessPoint(IBinder binder) throws RemoteException {
        if (mApBinder == binder) {
            Message message = Message.obtain();
            message.what = CarProjectionManager.PROJECTION_AP_STOPPED;
            mApMessenger.send(message);
        }
    }

    @Override
    public boolean requestBluetoothProfileInhibit(BluetoothDevice device, int profile,
            IBinder token) throws RemoteException {
        return true;
    }

    @Override
    public boolean releaseBluetoothProfileInhibit(BluetoothDevice device, int profile,
            IBinder token) throws RemoteException {
        return true;
    }

    @Override
    public void updateProjectionStatus(ProjectionStatus status, IBinder token)
            throws RemoteException {
        mCurrentProjectionStatus = status;
        mProjectionStatusMap.put(token, status);
        notifyStatusListeners(status,
                mStatusListeners.toArray(new ICarProjectionStatusListener[0]));
    }

    private void notifyStatusListeners(ProjectionStatus status,
            ICarProjectionStatusListener... listeners) throws RemoteException {
        for (ICarProjectionStatusListener listener : listeners) {
            listener.onProjectionStatusChanged(
                    status.getState(),
                    status.getPackageName(),
                    new ArrayList<>(mProjectionStatusMap.values()));
        }
    }

    @Override
    public void registerProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        mStatusListeners.add(listener);
        notifyStatusListeners(mCurrentProjectionStatus, listener);
    }

    @Override
    public void unregisterProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        mStatusListeners.remove(listener);
    }

    @Override
    public void setWifiConfiguration(WifiConfiguration wifiConfiguration) {
        mWifiConfiguration = wifiConfiguration;
    }

    @Override
    public Bundle getProjectionOptions() throws RemoteException {
        return mProjectionOptions.toBundle();
    }

    @Override
    public int[] getAvailableWifiChannels(int band) throws RemoteException {
        return new int[] {2412 /* Channel 1 */, 5180 /* Channel 36 */};
    }


    @Override
    public void setProjectionOptions(ProjectionOptions projectionOptions) {
        mProjectionOptions = projectionOptions;
    }
}
