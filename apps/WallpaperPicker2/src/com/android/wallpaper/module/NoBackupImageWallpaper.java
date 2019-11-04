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
package com.android.wallpaper.module;

import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE;

import android.annotation.SuppressLint;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.renderscript.Matrix4f;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.android.wallpaper.util.ScreenSizeCalculator;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import androidx.annotation.RequiresApi;

/**
 * Live wallpaper service which simply renders a wallpaper from internal storage. Designed as a
 * workaround to WallpaperManager not having an allowBackup=false option on pre-N builds of Android.
 * <p>
 * Adapted from {@code com.android.systemui.ImageWallpaper}.
 */
@SuppressLint("ServiceCast")
public class NoBackupImageWallpaper extends WallpaperService {

    public static final String ACTION_ROTATING_WALLPAPER_CHANGED =
            ".ACTION_ROTATING_WALLPAPER_CHANGED";
    public static final String PERMISSION_NOTIFY_ROTATING_WALLPAPER_CHANGED =
            ".NOTIFY_ROTATING_WALLPAPER_CHANGED";
    public static final String PREVIEW_WALLPAPER_FILE_PATH = "preview_wallpaper.jpg";
    public static final String ROTATING_WALLPAPER_FILE_PATH = "rotating_wallpaper.jpg";

    private static final String TAG = "NoBackupImageWallpaper";
    private static final String GL_LOG_TAG = "ImageWallpaperGL";
    private static final boolean DEBUG = false;
    private static final boolean FIXED_SIZED_SURFACE = false;

    private final Handler mHandler = new Handler();

    private int mOpenGlContextCounter;
    private WallpaperManager mWallpaperManager;
    private DrawableEngine mEngine;
    private boolean mIsHardwareAccelerated;

    @Override
    public void onCreate() {
        super.onCreate();

        mOpenGlContextCounter = 0;
        mWallpaperManager = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);

        // By default, use OpenGL for drawing the static wallpaper image.
        mIsHardwareAccelerated = true;
    }

    @Override
    public void onTrimMemory(int level) {
        if (mEngine != null) {
            mEngine.trimMemory(level);
        }
    }

    @Override
    public Engine onCreateEngine() {
        mEngine = new DrawableEngine();
        return mEngine;
    }

    private class DrawableEngine extends Engine {
        static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        static final int EGL_OPENGL_ES2_BIT = 4;
        private static final String S_SIMPLE_VS =
                "attribute vec4 position;\n"
                        + "attribute vec2 texCoords;\n"
                        + "varying vec2 outTexCoords;\n"
                        + "uniform mat4 projection;\n"
                        + "\nvoid main(void) {\n"
                        + "    outTexCoords = texCoords;\n"
                        + "    gl_Position = projection * position;\n"
                        + "}\n\n";
        private static final String S_SIMPLE_FS =
                "precision mediump float;\n\n"
                        + "varying vec2 outTexCoords;\n"
                        + "uniform sampler2D texture;\n"
                        + "\nvoid main(void) {\n"
                        + "    gl_FragColor = texture2D(texture, outTexCoords);\n"
                        + "}\n\n";
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        Bitmap mBackground;
        WallpaperColors mCachedWallpaperColors;
        int mBackgroundWidth = -1, mBackgroundHeight = -1;
        int mLastSurfaceWidth = -1, mLastSurfaceHeight = -1;
        int mLastRotation = -1;
        float mXOffset = 0.5f;
        float mYOffset = 0.5f;
        float mScale = 1f;
        boolean mVisible = true;
        boolean mOffsetsChanged;
        int mLastXTranslation;
        int mLastYTranslation;
        private Display mDefaultDisplay;
        private EGL10 mEgl;
        private EGLDisplay mEglDisplay;
        private EGLConfig mEglConfig;
        private EGLContext mEglContext;
        private EGLSurface mEglSurface;
        private int mTexture;
        private int mProgram;
        private boolean mIsOpenGlTextureLoaded;
        private int mRotationAtLastSurfaceSizeUpdate = -1;
        private int mDisplayWidthAtLastSurfaceSizeUpdate = -1;
        private int mDisplayHeightAtLastSurfaceSizeUpdate = -1;

        private int mLastRequestedWidth = -1;
        private int mLastRequestedHeight = -1;
        private AsyncTask<Void, Void, Bitmap> mLoader;
        private boolean mNeedsDrawAfterLoadingWallpaper;
        private boolean mSurfaceValid;

        private BroadcastReceiver mReceiver;

        public DrawableEngine() {
            super();
        }

        public void trimMemory(int level) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                    && mBackground != null) {
                if (DEBUG) {
                    Log.d(TAG, "trimMemory");
                }
                mBackground.recycle();
                mBackground = null;
                mBackgroundWidth = -1;
                mBackgroundHeight = -1;
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            if (DEBUG) {
                Log.d(TAG, "onCreate");
            }

            super.onCreate(surfaceHolder);

            mIsOpenGlTextureLoaded = false;

            mDefaultDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();

            updateSurfaceSize(surfaceHolder, mDefaultDisplay, false /* forDraw */);

            // Enable offset notifications to pan wallpaper for parallax effect.
            setOffsetNotificationsEnabled(true);

            // If not a preview, then register a local broadcast receiver for listening to changes in the
            // rotating wallpaper file.
            if (!isPreview()) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(getPackageName() + ACTION_ROTATING_WALLPAPER_CHANGED);

                mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (DEBUG) {
                            Log.i(TAG, "Broadcast received with intent: " + intent);
                        }

                        String action = intent.getAction();
                        if (action.equals(getPackageName() + ACTION_ROTATING_WALLPAPER_CHANGED)) {
                            DrawableEngine.this.invalidateAndRedrawWallpaper();
                        }
                    }
                };

                registerReceiver(mReceiver, filter, getPackageName()
                        + PERMISSION_NOTIFY_ROTATING_WALLPAPER_CHANGED, null /* handler */);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mBackground = null;
            mWallpaperManager.forgetLoadedWallpaper();

            if (!isPreview() && mReceiver != null) {
                unregisterReceiver(mReceiver);
            }
        }

        boolean updateSurfaceSize(SurfaceHolder surfaceHolder, Display display, boolean forDraw) {
            boolean hasWallpaper = true;
            Point displaySize = ScreenSizeCalculator.getInstance().getScreenSize(display);

            // Load background image dimensions, if we haven't saved them yet
            if (mBackgroundWidth <= 0 || mBackgroundHeight <= 0) {
                // Need to load the image to get dimensions
                loadWallpaper(forDraw);
                if (DEBUG) {
                    Log.d(TAG, "Reloading, redoing updateSurfaceSize later.");
                }
                hasWallpaper = false;
            }

            // Force the wallpaper to cover the screen in both dimensions
            int surfaceWidth = Math.max(displaySize.x, mBackgroundWidth);
            int surfaceHeight = Math.max(displaySize.y, mBackgroundHeight);

            if (FIXED_SIZED_SURFACE) {
                // Used a fixed size surface, because we are special.  We can do
                // this because we know the current design of window animations doesn't
                // cause this to break.
                surfaceHolder.setFixedSize(surfaceWidth, surfaceHeight);
                mLastRequestedWidth = surfaceWidth;
                mLastRequestedHeight = surfaceHeight;
            } else {
                surfaceHolder.setSizeFromLayout();
            }
            return hasWallpaper;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (DEBUG) {
                Log.d(TAG, "onVisibilityChanged: mVisible, visible=" + mVisible + ", " + visible);
            }

            if (mVisible != visible) {
                if (DEBUG) {
                    Log.d(TAG, "Visibility changed to visible=" + visible);
                }
                mVisible = visible;
                drawFrame(false /* forceRedraw */);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xOffsetStep, float yOffsetStep,
                                     int xPixels, int yPixels) {
            if (DEBUG) {
                Log.d(TAG, "onOffsetsChanged: xOffset=" + xOffset + ", yOffset=" + yOffset
                        + ", xOffsetStep=" + xOffsetStep + ", yOffsetStep=" + yOffsetStep
                        + ", xPixels=" + xPixels + ", yPixels=" + yPixels);
            }

            if (mXOffset != xOffset || mYOffset != yOffset) {
                if (DEBUG) {
                    Log.d(TAG, "Offsets changed to (" + xOffset + "," + yOffset + ").");
                }
                mXOffset = xOffset;
                mYOffset = yOffset;
                mOffsetsChanged = true;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    drawFrame(false /* forceRedraw */);
                }
            });
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceChanged: width=" + width + ", height=" + height);
            }

            super.onSurfaceChanged(holder, format, width, height);

            // Retrieve buffer in new size.
            if (mEgl != null) {
                mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
            }
            drawFrame(false /* forceRedraw */);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            if (DEBUG) {
                Log.d(TAG, "onSurfaceDestroyed");
            }
            mLastSurfaceWidth = mLastSurfaceHeight = -1;
            mSurfaceValid = false;

            if (mIsHardwareAccelerated) {
                finishGL(mTexture, mProgram);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            if (DEBUG) {
                Log.d(TAG, "onSurfaceCreated");
            }
            mLastSurfaceWidth = mLastSurfaceHeight = -1;
            mSurfaceValid = true;

            if (mIsHardwareAccelerated) {
                if (!initGL(holder)) {
                    // Fall back to canvas drawing if initializing OpenGL failed.
                    mIsHardwareAccelerated = false;
                    mEgl = null;
                }
            }
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceRedrawNeeded");
            }
            super.onSurfaceRedrawNeeded(holder);

            drawFrame(true /* forceRedraw */);
        }

        @RequiresApi(VERSION_CODES.O_MR1)
        @Override
        public WallpaperColors onComputeColors() {
            // It's OK to return null here.
            return mCachedWallpaperColors;
        }

        /**
         * Invalidates the currently-drawn wallpaper image, causing the engine to reload the image from
         * disk and draw the new wallpaper image.
         */
        public void invalidateAndRedrawWallpaper() {
            // If a wallpaper load was already in flight, cancel it and restart a load in order to decode
            // the new image.
            if (mLoader != null) {
                mLoader.cancel(true /* mayInterruptIfRunning */);
                mLoader = null;
            }

            loadWallpaper(true /* needsDraw */);
        }

        void drawFrame(boolean forceRedraw) {
            if (!mSurfaceValid) {
                return;
            }

            Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(mDefaultDisplay);
            int newRotation = mDefaultDisplay.getRotation();

            // Sometimes a wallpaper is not large enough to cover the screen in one dimension.
            // Call updateSurfaceSize -- it will only actually do the update if the dimensions
            // should change
            if (newRotation != mLastRotation) {
                // Update surface size (if necessary)
                if (!updateSurfaceSize(getSurfaceHolder(), mDefaultDisplay, true /* forDraw */)) {
                    return;
                }
                mRotationAtLastSurfaceSizeUpdate = newRotation;
                mDisplayWidthAtLastSurfaceSizeUpdate = screenSize.x;
                mDisplayHeightAtLastSurfaceSizeUpdate = screenSize.y;
            }
            SurfaceHolder sh = getSurfaceHolder();
            final Rect frame = sh.getSurfaceFrame();
            final int dw = frame.width();
            final int dh = frame.height();
            boolean surfaceDimensionsChanged = dw != mLastSurfaceWidth
                    || dh != mLastSurfaceHeight;

            boolean redrawNeeded = surfaceDimensionsChanged || newRotation != mLastRotation
                    || forceRedraw;
            if (!redrawNeeded && !mOffsetsChanged) {
                if (DEBUG) {
                    Log.d(TAG, "Suppressed drawFrame since redraw is not needed "
                            + "and offsets have not changed.");
                }
                return;
            }
            mLastRotation = newRotation;

            // Load bitmap if its null and we're not using hardware acceleration.
            if ((mIsHardwareAccelerated && !mIsOpenGlTextureLoaded) // Using OpenGL but texture not loaded
                    || (!mIsHardwareAccelerated && mBackground == null)) { // Draw with Canvas but no bitmap
                if (DEBUG) {
                    Log.d(TAG, "Reloading bitmap: mBackground, bgw, bgh, dw, dh = "
                            + mBackground + ", " + ((mBackground == null) ? 0 : mBackground.getWidth()) + ", "
                            + ((mBackground == null) ? 0 : mBackground.getHeight()) + ", " + dw + ", " + dh);
                }
                loadWallpaper(true /* needDraw */);
                if (DEBUG) {
                    Log.d(TAG, "Reloading, resuming draw later");
                }
                return;
            }

            // Center the scaled image
            mScale = Math.max(1f, Math.max(dw / (float) mBackgroundWidth,
                    dh / (float) mBackgroundHeight));
            final int availw = dw - (int) (mBackgroundWidth * mScale);
            final int availh = dh - (int) (mBackgroundHeight * mScale);
            int xPixels = availw / 2;
            int yPixels = availh / 2;

            // Adjust the image for xOffset/yOffset values. If window manager is handling offsets,
            // mXOffset and mYOffset are set to 0.5f by default and therefore xPixels and yPixels
            // will remain unchanged
            final int availwUnscaled = dw - mBackgroundWidth;
            final int availhUnscaled = dh - mBackgroundHeight;
            if (availwUnscaled < 0) {
                xPixels += (int) (availwUnscaled * (mXOffset - .5f) + .5f);
            }
            if (availhUnscaled < 0) {
                yPixels += (int) (availhUnscaled * (mYOffset - .5f) + .5f);
            }

            mOffsetsChanged = false;
            if (surfaceDimensionsChanged) {
                mLastSurfaceWidth = dw;
                mLastSurfaceHeight = dh;
            }
            if (!redrawNeeded && xPixels == mLastXTranslation && yPixels == mLastYTranslation) {
                if (DEBUG) {
                    Log.d(TAG, "Suppressed drawFrame since the image has not "
                            + "actually moved an integral number of pixels.");
                }
                return;
            }
            mLastXTranslation = xPixels;
            mLastYTranslation = yPixels;

            if (DEBUG) {
                Log.d(TAG, "Redrawing wallpaper");
            }

            if (mIsHardwareAccelerated) {
                if (!drawWallpaperWithOpenGL(sh, availw, availh, xPixels, yPixels)) {
                    drawWallpaperWithCanvas(sh, availw, availh, xPixels, yPixels);
                } else {
                    // If OpenGL drawing was successful, then we can safely discard a reference to the
                    // wallpaper bitmap to save memory (since a copy has already been loaded into an OpenGL
                    // texture).
                    mBackground = null;
                }
            } else {
                drawWallpaperWithCanvas(sh, availw, availh, xPixels, yPixels);
            }
        }

        /**
         * Loads the wallpaper on background thread and schedules updating the surface frame,
         * and if {@param needsDraw} is set also draws a frame.
         * <p>
         * If loading is already in-flight, subsequent loads are ignored (but needDraw is or-ed to
         * the active request).
         * <p>
         * If {@param needsReset} is set also clears the cache in WallpaperManager first.
         */
        private void loadWallpaper(boolean needsDraw) {
            mNeedsDrawAfterLoadingWallpaper |= needsDraw;
            if (mLoader != null) {
                if (DEBUG) {
                    Log.d(TAG, "Skipping loadWallpaper, already in flight ");
                }
                return;
            }
            mLoader = new AsyncTask<Void, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Void... params) {
                    Throwable exception = null;
                    try {
                        // Decode bitmap of rotating image wallpaper.
                        String wallpaperFilePath = isPreview()
                                ? PREVIEW_WALLPAPER_FILE_PATH : ROTATING_WALLPAPER_FILE_PATH;
                        Context context = isPreview() ? getApplicationContext()
                                : getApplicationContext().createDeviceProtectedStorageContext();
                        FileInputStream fileInputStream = context.openFileInput(wallpaperFilePath);
                        Bitmap bitmap = BitmapFactory.decodeStream(fileInputStream);
                        fileInputStream.close();
                        return bitmap;
                    } catch (RuntimeException | FileNotFoundException | OutOfMemoryError e) {
                        Log.i(TAG, "couldn't decode stream: ", e);
                        exception = e;
                    } catch (IOException e) {
                        Log.i(TAG, "couldn't close stream: ", e);
                        exception = e;
                    }

                    if (isCancelled()) {
                        return null;
                    }

                    if (exception != null) {
                        // Note that if we do fail at this, and the default wallpaper can't
                        // be loaded, we will go into a cycle.  Don't do a build where the
                        // default wallpaper can't be loaded.
                        Log.w(TAG, "Unable to load wallpaper!", exception);
                        try {
                            return ((BitmapDrawable) getFallbackDrawable()).getBitmap();
                        } catch (OutOfMemoryError ex) {
                            // now we're really screwed.
                            Log.w(TAG, "Unable reset to default wallpaper!", ex);
                        }

                        if (isCancelled()) {
                            return null;
                        }
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Bitmap b) {
                    mBackground = null;
                    mBackgroundWidth = -1;
                    mBackgroundHeight = -1;

                    if (b != null) {
                        mBackground = b;
                        mBackgroundWidth = mBackground.getWidth();
                        mBackgroundHeight = mBackground.getHeight();

                        if (VERSION.SDK_INT >= VERSION_CODES.O_MR1) {
                            mCachedWallpaperColors = WallpaperColors.fromBitmap(mBackground);
                            notifyColorsChanged();
                        }
                    }

                    if (DEBUG) {
                        Log.d(TAG, "Wallpaper loaded: " + mBackground);
                    }
                    updateSurfaceSize(getSurfaceHolder(), mDefaultDisplay,
                            false /* forDraw */);
                    if (mTexture != 0 && mEgl != null) {
                        deleteTexture(mTexture);
                    }
                    // If background is absent (due to an error decoding the bitmap) then don't try to load
                    // a texture.
                    if (mEgl != null && mBackground != null) {
                        mTexture = loadTexture(mBackground);
                    }
                    if (mNeedsDrawAfterLoadingWallpaper) {
                        drawFrame(true /* forceRedraw */);
                    }

                    mLoader = null;
                    mNeedsDrawAfterLoadingWallpaper = false;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        private Drawable getFallbackDrawable() {
            Drawable drawable;
            try {
                drawable = mWallpaperManager.getDrawable();
            } catch (java.lang.Exception e) {
                // Work around Samsung bug where SecurityException is thrown if device is still using its
                // default wallpaper, and around Android 7.0 bug where SELinux issues can cause a perfectly
                // valid access of the current wallpaper to cause a failed Binder transaction manifest here
                // as a RuntimeException.
                drawable = mWallpaperManager.getBuiltInDrawable();
            }
            return drawable;
        }

        @Override
        protected void dump(String prefix, FileDescriptor fd, PrintWriter out, String[] args) {
            super.dump(prefix, fd, out, args);

            out.print(prefix);
            out.println("ImageWallpaper.DrawableEngine:");
            out.print(prefix);
            out.print(" mBackground=");
            out.print(mBackground);
            out.print(" mBackgroundWidth=");
            out.print(mBackgroundWidth);
            out.print(" mBackgroundHeight=");
            out.println(mBackgroundHeight);

            out.print(prefix);
            out.print(" mLastRotation=");
            out.print(mLastRotation);
            out.print(" mLastSurfaceWidth=");
            out.print(mLastSurfaceWidth);
            out.print(" mLastSurfaceHeight=");
            out.println(mLastSurfaceHeight);

            out.print(prefix);
            out.print(" mXOffset=");
            out.print(mXOffset);
            out.print(" mYOffset=");
            out.println(mYOffset);

            out.print(prefix);
            out.print(" mVisible=");
            out.print(mVisible);
            out.print(" mOffsetsChanged=");
            out.println(mOffsetsChanged);

            out.print(prefix);
            out.print(" mLastXTranslation=");
            out.print(mLastXTranslation);
            out.print(" mLastYTranslation=");
            out.print(mLastYTranslation);
            out.print(" mScale=");
            out.println(mScale);

            out.print(prefix);
            out.print(" mLastRequestedWidth=");
            out.print(mLastRequestedWidth);
            out.print(" mLastRequestedHeight=");
            out.println(mLastRequestedHeight);

            out.print(prefix);
            out.println(" DisplayInfo at last updateSurfaceSize:");
            out.print(prefix);
            out.print("  rotation=");
            out.print(mRotationAtLastSurfaceSizeUpdate);
            out.print("  width=");
            out.print(mDisplayWidthAtLastSurfaceSizeUpdate);
            out.print("  height=");
            out.println(mDisplayHeightAtLastSurfaceSizeUpdate);
        }

        private void drawWallpaperWithCanvas(SurfaceHolder sh, int w, int h, int left, int top) {
            Canvas c = sh.lockCanvas();
            if (c != null) {
                try {
                    if (DEBUG) {
                        Log.d(TAG, "Redrawing: left=" + left + ", top=" + top);
                    }

                    final float right = left + mBackgroundWidth * mScale;
                    final float bottom = top + mBackgroundHeight * mScale;
                    if (w < 0 || h < 0) {
                        c.save();
                        c.clipOutRect(left, top, right, bottom);
                        c.drawColor(0xff000000);
                        c.restore();
                    }
                    if (mBackground != null) {
                        RectF dest = new RectF(left, top, right, bottom);
                        // add a filter bitmap?
                        c.drawBitmap(mBackground, null, dest, null);
                    }
                } finally {
                    sh.unlockCanvasAndPost(c);
                }
            }
        }

        private boolean drawWallpaperWithOpenGL(SurfaceHolder sh, int w, int h, int left, int top) {

            mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);

            final float right = left + mBackgroundWidth * mScale;
            final float bottom = top + mBackgroundHeight * mScale;

            final Rect frame = sh.getSurfaceFrame();
            final Matrix4f ortho = new Matrix4f();
            ortho.loadOrtho(0.0f, frame.width(), frame.height(), 0.0f, -1.0f, 1.0f);

            final FloatBuffer triangleVertices = createMesh(left, top, right, bottom);

            final int attribPosition = GLES20.glGetAttribLocation(mProgram, "position");
            final int attribTexCoords = GLES20.glGetAttribLocation(mProgram, "texCoords");
            final int uniformTexture = GLES20.glGetUniformLocation(mProgram, "texture");
            final int uniformProjection = GLES20.glGetUniformLocation(mProgram, "projection");

            checkGlError();

            GLES20.glViewport(0, 0, frame.width(), frame.height());
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);

            GLES20.glUseProgram(mProgram);
            GLES20.glEnableVertexAttribArray(attribPosition);
            GLES20.glEnableVertexAttribArray(attribTexCoords);
            GLES20.glUniform1i(uniformTexture, 0);
            GLES20.glUniformMatrix4fv(uniformProjection, 1, false, ortho.getArray(), 0);

            checkGlError();

            if (w > 0 || h > 0) {
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }

            // drawQuad
            triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(attribPosition, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);

            triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(attribTexCoords, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            boolean status = mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
            checkEglError();

            return status;
        }

        private FloatBuffer createMesh(int left, int top, float right, float bottom) {
            final float[] verticesData = {
                    // X, Y, Z, U, V
                    left, bottom, 0.0f, 0.0f, 1.0f,
                    right, bottom, 0.0f, 1.0f, 1.0f,
                    left, top, 0.0f, 0.0f, 0.0f,
                    right, top, 0.0f, 1.0f, 0.0f,
            };

            final int bytes = verticesData.length * FLOAT_SIZE_BYTES;
            final FloatBuffer triangleVertices = ByteBuffer.allocateDirect(bytes).order(
                    ByteOrder.nativeOrder()).asFloatBuffer();
            triangleVertices.put(verticesData).position(0);
            return triangleVertices;
        }

        private int loadTexture(Bitmap bitmap) {
            mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);

            int[] textures = new int[1];

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glGenTextures(1, textures, 0);
            checkGlError();

            int texture = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            checkGlError();

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLUtils.texImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, GLES20.GL_UNSIGNED_BYTE, 0);
            checkGlError();

            mIsOpenGlTextureLoaded = true;

            return texture;
        }

        private int buildProgram(String vertex, String fragment) {
            int vertexShader = buildShader(vertex, GLES20.GL_VERTEX_SHADER);
            if (vertexShader == 0) {
                return 0;
            }

            int fragmentShader = buildShader(fragment, GLES20.GL_FRAGMENT_SHADER);
            if (fragmentShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            checkGlError();

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);

            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] != GLES20.GL_TRUE) {
                String error = GLES20.glGetProgramInfoLog(program);
                Log.d(GL_LOG_TAG, "Error while linking program:\n" + error);
                GLES20.glDeleteProgram(program);
                return 0;
            }

            return program;
        }

        private int buildShader(String source, int type) {
            int shader = GLES20.glCreateShader(type);

            GLES20.glShaderSource(shader, source);
            checkGlError();

            GLES20.glCompileShader(shader);
            checkGlError();

            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] != GLES20.GL_TRUE) {
                String error = GLES20.glGetShaderInfoLog(shader);
                Log.d(GL_LOG_TAG, "Error while compiling shader:\n" + error);
                GLES20.glDeleteShader(shader);
                return 0;
            }

            return shader;
        }

        private void checkEglError() {
            int error = mEgl.eglGetError();
            if (error != EGL10.EGL_SUCCESS) {
                Log.w(GL_LOG_TAG, "EGL error = " + GLUtils.getEGLErrorString(error));
            }
        }

        private void checkGlError() {
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                Log.w(GL_LOG_TAG, "GL error = 0x" + Integer.toHexString(error), new Throwable());
            }
        }

        private void deleteTexture(int texture) {
            int[] textures = new int[1];
            textures[0] = texture;

            mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
            GLES20.glDeleteTextures(1, textures, 0);
            mTexture = 0;
        }

        private void finishGL(int texture, int program) {
            if (mEgl == null) {
                return;
            }

            mOpenGlContextCounter--;

            mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
            deleteTexture(mTexture);
            GLES20.glDeleteProgram(program);
            mEgl.eglMakeCurrent(mEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
            mEgl.eglDestroyContext(mEglDisplay, mEglContext);
            if (mOpenGlContextCounter == 0) {
                mEgl.eglTerminate(mEglDisplay);
            }

            mEgl = null;
        }

        private boolean initGL(SurfaceHolder surfaceHolder) {
            mEgl = (EGL10) EGLContext.getEGL();

            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("eglGetDisplay failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) {
                throw new RuntimeException("eglInitialize failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            mOpenGlContextCounter++;

            mEglConfig = chooseEglConfig();
            if (mEglConfig == null) {
                throw new RuntimeException("eglConfig not initialized");
            }

            mEglContext = createContext(mEgl, mEglDisplay, mEglConfig);
            if (mEglContext == EGL_NO_CONTEXT) {
                throw new RuntimeException("createContext failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            int attribs[] = {
                    EGL10.EGL_WIDTH, 1,
                    EGL10.EGL_HEIGHT, 1,
                    EGL10.EGL_NONE
            };
            EGLSurface tmpSurface = mEgl.eglCreatePbufferSurface(mEglDisplay, mEglConfig, attribs);
            mEgl.eglMakeCurrent(mEglDisplay, tmpSurface, tmpSurface, mEglContext);

            int[] maxSize = new int[1];
            Rect frame = surfaceHolder.getSurfaceFrame();
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);

            mEgl.eglMakeCurrent(
                    mEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, tmpSurface);

            if (frame.width() > maxSize[0] || frame.height() > maxSize[0]) {
                mEgl.eglDestroyContext(mEglDisplay, mEglContext);
                mEgl.eglTerminate(mEglDisplay);
                Log.e(GL_LOG_TAG, "requested  texture size " + frame.width() + "x" + frame.height()
                        + " exceeds the support maximum of " + maxSize[0] + "x" + maxSize[0]);
                return false;
            }

            mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, mEglConfig, surfaceHolder, null);
            if (mEglSurface == null || mEglSurface == EGL_NO_SURFACE) {
                int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW || error == EGL10.EGL_BAD_ALLOC) {
                    Log.e(GL_LOG_TAG, "createWindowSurface returned " + GLUtils.getEGLErrorString(error)
                            + ".");
                    return false;
                }
                throw new RuntimeException("createWindowSurface failed "
                        + GLUtils.getEGLErrorString(error));
            }

            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                throw new RuntimeException("eglMakeCurrent failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            mProgram = buildProgram(S_SIMPLE_VS, S_SIMPLE_FS);
            if (mBackground != null) {
                mTexture = loadTexture(mBackground);
            }

            return true;
        }


        EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
            int[] attribList = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            return egl.eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, attribList);
        }

        private EGLConfig chooseEglConfig() {
            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = getConfig();
            if (!mEgl.eglChooseConfig(mEglDisplay, configSpec, configs, 1, configsCount)) {
                throw new IllegalArgumentException("eglChooseConfig failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            } else if (configsCount[0] > 0) {
                return configs[0];
            }
            return null;
        }

        private int[] getConfig() {
            return new int[]{
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 0,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_CONFIG_CAVEAT, EGL10.EGL_NONE,
                    EGL10.EGL_NONE
            };
        }
    }
}
