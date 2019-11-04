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

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * This class encapsulates all work related to managing networked virtual display.
 * <p>
 * It opens a socket and listens on port {@code PORT} for connections, or the emulator pipe. Once
 * connection is established it creates virtual display and media encoder and starts streaming video
 * to that socket.  If the receiving part is disconnected, it will keep port open and virtual
 * display won't be destroyed.
 */
public class NetworkedVirtualDisplay {
    private static final String TAG = "Cluster." + NetworkedVirtualDisplay.class.getSimpleName();

    private final String mUniqueId =  UUID.randomUUID().toString();

    private final DisplayManager mDisplayManager;
    private final int mWidth;
    private final int mHeight;
    private final int mDpi;

    private static final int FPS = 25;
    private static final int BITRATE = 6144000;
    private static final String MEDIA_FORMAT_MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    public static final int MSG_START = 0;
    public static final int MSG_STOP = 1;
    public static final int MSG_SEND_FRAME = 2;

    private static final String PIPE_NAME = "pipe:qemud:carCluster";
    private static final String PIPE_DEVICE = "/dev/qemu_pipe";

    // Constants shared with emulator in car-cluster-widget.cpp
    public static final int PIPE_START = 1;
    public static final int PIPE_STOP = 2;

    private static final int PORT = 5151;

    private SenderThread mActiveThread;
    private HandlerThread mBroadcastThread = new HandlerThread("BroadcastThread");

    private VirtualDisplay mVirtualDisplay;
    private MediaCodec mVideoEncoder;
    private Handler mHandler;
    private byte[] mBuffer = null;
    private int mLastFrameLength = 0;

    private final DebugCounter mCounter = new DebugCounter();

    NetworkedVirtualDisplay(Context context, int width, int height, int dpi) {
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mWidth = width;
        mHeight = height;
        mDpi = dpi;

        DisplayListener displayListener = new DisplayListener() {
            @Override
            public void onDisplayAdded(int i) {
                final Display display = mDisplayManager.getDisplay(i);
                if (display != null && getDisplayName().equals(display.getName())) {
                    onVirtualDisplayReady(display);
                }
            }

            @Override
            public void onDisplayRemoved(int i) {}

            @Override
            public void onDisplayChanged(int i) {}
        };

        mDisplayManager.registerDisplayListener(displayListener, new Handler());
    }

    /**
     * Opens socket and creates virtual display asynchronously once connection established.  Clients
     * of this class may subscribe to
     * {@link android.hardware.display.DisplayManager#registerDisplayListener(
     * DisplayListener, Handler)} to be notified when virtual display is created.
     * Note, that this method should be called only once.
     *
     * @return Unique display name associated with the instance of this class.
     *
     * @see {@link Display#getName()}
     *
     * @throws IllegalStateException thrown if networked display already started
     */
    public String start() {
        if (mBroadcastThread.isAlive()) {
            throw new IllegalStateException("Already started");
        }

        mBroadcastThread.start();
        mHandler = new BroadcastThreadHandler(mBroadcastThread.getLooper());
        mHandler.sendMessage(Message.obtain(mHandler, MSG_START));
        return getDisplayName();
    }

    public void release() {
        mHandler.sendMessage(Message.obtain(mHandler, MSG_STOP));
        mBroadcastThread.quitSafely();

        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(null);
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private String getDisplayName() {
        return "Cluster-" + mUniqueId;
    }

    private VirtualDisplay createVirtualDisplay() {
        Log.i(TAG, "createVirtualDisplay " + mWidth + "x" + mHeight +"@" + mDpi);
        return mDisplayManager.createVirtualDisplay(getDisplayName(), mWidth, mHeight, mDpi,
                null, 0 /* flags */, null, null );
    }

    private void onVirtualDisplayReady(Display display) {
        Log.i(TAG, "onVirtualDisplayReady, display: " + display);
    }

    private void startCasting(Handler handler) {
        Log.i(TAG, "Start casting...");
        if (mVideoEncoder != null) {
            Log.i(TAG, "Already started casting");
            return;
        }
        mVideoEncoder = createVideoStream(handler);

        if (mVirtualDisplay == null) {
            mVirtualDisplay = createVirtualDisplay();
        }

        mVirtualDisplay.setSurface(mVideoEncoder.createInputSurface());
        mVideoEncoder.start();
        Log.i(TAG, "Video encoder started");
    }

    private MediaCodec createVideoStream(Handler handler) {
        MediaCodec encoder;
        try {
            encoder = MediaCodec.createEncoderByType(MEDIA_FORMAT_MIMETYPE);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create video encoder for " + MEDIA_FORMAT_MIMETYPE, e);
            return null;
        }

        encoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                // Nothing to do
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index,
                    @NonNull BufferInfo info) {
                mCounter.outputBuffers++;
                doOutputBufferAvailable(index, info);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull CodecException e) {
                Log.e(TAG, "onError, codec: " + codec, e);
                mCounter.bufferErrors++;
                stopCasting();
                startCasting(handler);
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec,
                    @NonNull MediaFormat format) {
                Log.i(TAG, "onOutputFormatChanged, codec: " + codec + ", format: " + format);

            }
        }, handler);

        configureVideoEncoder(encoder, mWidth, mHeight);
        return encoder;
    }

    private void doOutputBufferAvailable(int index, @NonNull BufferInfo info) {
        mHandler.removeMessages(MSG_SEND_FRAME);

        ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(index);
        if (encodedData == null) {
            throw new RuntimeException("couldn't fetch buffer at index " + index);
        }

        if (info.size != 0) {
            encodedData.position(info.offset);
            encodedData.limit(info.offset + info.size);
            mLastFrameLength = encodedData.remaining();
            if (mBuffer == null || mBuffer.length < mLastFrameLength) {
                Log.i(TAG, "Allocating new buffer: " + mLastFrameLength);
                mBuffer = new byte[mLastFrameLength];
            }
            encodedData.get(mBuffer, 0, mLastFrameLength);
            mVideoEncoder.releaseOutputBuffer(index, false);

            // Send this frame asynchronously (avoiding blocking on the socket). We might miss
            // frames if the consumer is not fast enough, but this is acceptable.
            sendFrameAsync(0);
        } else {
            Log.e(TAG, "Skipping empty buffer");
            mVideoEncoder.releaseOutputBuffer(index, false);
        }
    }

    private void sendFrameAsync(long delayMs) {
        Message msg = mHandler.obtainMessage(MSG_SEND_FRAME);
        mHandler.sendMessageDelayed(msg, delayMs);
    }

    private void sendFrame(byte[] buf, int len) {
        if (mActiveThread != null) {
            mActiveThread.send(buf, len);
        }
    }

    private void stopCasting() {
        Log.i(TAG, "Stopping casting...");

        if (mVirtualDisplay != null) {
            Surface surface = mVirtualDisplay.getSurface();
            if (surface != null) surface.release();
        }

        if (mVideoEncoder != null) {
            // Releasing encoder as stop/start didn't work well (couldn't create or reuse input
            // surface).
            try {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            } catch (IllegalStateException e) {
                // do nothing, already released
            }
            mVideoEncoder = null;
        }
        Log.i(TAG, "Casting stopped");
    }

    private class BroadcastThreadHandler extends Handler {
        private static final int MAX_FAIL_COUNT = 10;
        private int mFailConnectCounter;

        BroadcastThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START:
                    Log.i(TAG, "Received start message");

                    // Make sure mActiveThread cannot start multiple times
                    if (mActiveThread != null) {
                        Log.w(TAG, "Trying to start a running thread. Race condition may exist");
                        break;
                    }

                    // Failure to connect to either pipe or network returns null
                    if (mActiveThread == null) {
                        mActiveThread = tryPipeConnect();
                    }
                    if (mActiveThread == null) {
                        mActiveThread = tryNetworkConnect();
                    }
                    if (mActiveThread == null) {
                        // When failed attempt limit is reached, clean up and quit this thread.
                        mFailConnectCounter++;
                        if (mFailConnectCounter >= MAX_FAIL_COUNT) {
                            Log.e(TAG, "Too many failed connection attempts; aborting");
                            release();
                            throw new RuntimeException("Abort after failed connection attempts");
                        }
                        mHandler.sendMessage(Message.obtain(mHandler, MSG_START));
                        break;
                    }

                    try {
                        mFailConnectCounter = 0;
                        mCounter.clientsConnected++;
                        mActiveThread.start();
                        startCasting(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start thread", e);
                        Log.e(TAG, "DebugCounter: " + mCounter);
                    }
                    break;

                case MSG_STOP:
                    Log.i(TAG, "Received stop message");
                    stopCasting();
                    mCounter.clientsDisconnected++;
                    if (mActiveThread != null) {
                        mActiveThread.close();
                        try {
                            mActiveThread.join();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Waiting for active thread to close failed", e);
                        }
                        mActiveThread = null;
                    }
                    break;

                case MSG_SEND_FRAME:
                    if (mActiveThread == null) {
                        // Stop the chaining signal if there's no client to send to
                        break;
                    }
                    sendFrame(mBuffer, mLastFrameLength);
                    // We will keep sending last frame every second as a heartbeat.
                    sendFrameAsync(1000L);
                    break;
            }
        }

        // Returns null if can't establish pipe connection
        // Otherwise returns the corresponding client thread
        private PipeThread tryPipeConnect() {
            try {
                RandomAccessFile pipe = new RandomAccessFile(PIPE_DEVICE, "rw");
                byte[] temp = new byte[PIPE_NAME.length() + 1];
                temp[PIPE_NAME.length()] = 0;
                System.arraycopy(PIPE_NAME.getBytes(), 0, temp, 0, PIPE_NAME.length());
                pipe.write(temp);

                // At this point, the pipe exists, so we will just wait for a start signal
                // This is in case pipe still sends leftover stops from last instantiation
                int signal = pipe.read();
                while (signal != PIPE_START) {
                    Log.i(TAG, "Received non-start signal: " + signal);
                    signal = pipe.read();
                }
                return new PipeThread(mHandler, pipe);
            } catch (IOException e) {
                Log.e(TAG, "Failed to establish pipe connection", e);
                return null;
            }
        }

        // Returns null if can't establish network connection
        // Otherwise returns the corresponding client thread
        private SocketThread tryNetworkConnect() {
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "Server socket opened");
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoLinger(true, 0);

                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();

                return new SocketThread(mHandler, serverSocket, inputStream, outputStream);
            } catch (IOException e) {
                Log.e(TAG, "Failed to establish network connection", e);
                return null;
            }
        }
    }

    private static void configureVideoEncoder(MediaCodec codec, int width, int height) {
        MediaFormat format = MediaFormat.createVideoFormat(MEDIA_FORMAT_MIMETYPE, width, height);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, FPS);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 second between I-frames
        format.setInteger(MediaFormat.KEY_LEVEL, CodecProfileLevel.AVCLevel31);
        format.setInteger(MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    @Override
    public String toString() {
        return getClass() + "{"
                + ", receiver connected: " + (mActiveThread != null)
                + ", encoder: " + mVideoEncoder
                + ", virtualDisplay" + mVirtualDisplay
                + "}";
    }

    private static class DebugCounter {
        long outputBuffers;
        long bufferErrors;
        long clientsConnected;
        long clientsDisconnected;

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{"
                    + "outputBuffers=" + outputBuffers
                    + ", bufferErrors=" + bufferErrors
                    + ", clientsConnected=" + clientsConnected
                    + ", clientsDisconnected= " + clientsDisconnected
                    + "}";
        }
    }
}
