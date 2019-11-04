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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Thread that can send data to the emulator using a qemud service.
 */
public class PipeThread extends SenderThread {
    private static final String TAG = "Cluster." + PipeThread.class.getSimpleName();

    private RandomAccessFile mPipe;

    /**
     * Creates instance of pipe thread that can write to given pipe file.
     *
     * @param handler {@link Handler} used to message broadcaster.
     * @param pipe {@link RandomAccessFile} file already connected to pipe.
     */
    PipeThread(Handler handler, RandomAccessFile pipe) {
        super(handler);
        mPipe = pipe;
    }

    public void run() {
        try {
            int signal = mPipe.read();
            while (signal != NetworkedVirtualDisplay.PIPE_STOP) {
                Log.i(TAG, "Received non-stop signal: " + signal);
                signal = mPipe.read();
            }
            restart();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read from pipe");
            restart();
        }
    }

    @Override
    public void send(byte[] buf, int len) {
        try {
            // First sends the size prior to sending the data, since receiving side only sees
            // the size of the buffer, which could be significant larger than the actual data.
            mPipe.write(ByteBuffer.allocate(4)
                          .order(ByteOrder.LITTLE_ENDIAN).putInt(len).array());
            mPipe.write(buf);
        } catch (IOException e) {
            Log.e(TAG, "Write to pipe failed");
            restart();
        }
    }

    @Override
    public void close() {
        try {
            mPipe.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close pipe", e);
        }
    }
}

