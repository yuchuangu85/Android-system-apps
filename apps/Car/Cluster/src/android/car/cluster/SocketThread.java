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
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

/**
 * The thread that will send data on an opened socket.
 */
public class SocketThread extends SenderThread {
    private static final String TAG = "Cluster." + SocketThread.class.getSimpleName();
    private ServerSocket mServerSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;

    /**
     * Create instance of thread that can write to given open socket.
     *
     * @param handler {@link Handler} used to message the broadcaster.
     * @param serverSocket {@link ServerSocket} should be already opened.
     * @param inputStream {@link InputStream} corresponding to opened socket.
     * @param outputStream {@link OutputStream} corresponding to opened socket.
     */
    SocketThread(Handler handler, ServerSocket serverSocket, InputStream inputStream,
                    OutputStream outputStream) {
        super(handler);
        mServerSocket = serverSocket;
        mInputStream = inputStream;
        mOutputStream = outputStream;
    }

    public void run() {
        try {
            // This read should block until something disconnects (or something
            // similar) which should cause an exception, in which case we should
            // try to setup again and reconnect
            mInputStream.read();
        } catch (IOException e) {
            Log.e(TAG, "Socket thread disconnected.");
        }
        restart();
    }

    @Override
    public void send(byte[] buf, int len) {
        try {
            mOutputStream.write(buf, 0, len);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write data to socket, retrying connection");
            restart();
        }
    }

    @Override
    public void close() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close server socket, ignoring");
            }
            mServerSocket = null;
        }
        mInputStream = null;
        mOutputStream = null;
    }
}

