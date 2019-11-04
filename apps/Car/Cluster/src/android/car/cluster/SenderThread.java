/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.car.cluster;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class serves as a template for sending to specific clients of the broadcaster.
 */
public abstract class SenderThread extends Thread {
    private static final String TAG = "Cluster.SenderThread";

    private Handler mHandler;

    SenderThread(Handler handler) {
        mHandler = handler;
    }

    abstract void send(byte[] buf, int len);
    abstract void close();

    /**
     * Tells the broadcasting thread to stop and close everything in progress, and start over again.
     * It will kill the current instance of this thread, and produce a new one.
     */
    synchronized void restart() {
        if (mHandler.hasMessages(NetworkedVirtualDisplay.MSG_START)) return;
        Log.i(TAG, "Sending STOP and START msgs to NetworkedVirtualDisplay");

        mHandler.sendMessage(Message.obtain(mHandler, NetworkedVirtualDisplay.MSG_STOP));
        mHandler.sendMessage(Message.obtain(mHandler, NetworkedVirtualDisplay.MSG_START));
    }
}
