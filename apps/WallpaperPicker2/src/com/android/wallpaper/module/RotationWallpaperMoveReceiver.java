package com.android.wallpaper.module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.util.DiskBasedLogger;
import com.android.wallpaper.util.FileMover;

import java.io.File;
import java.io.IOException;

/**
 * Receiver to run when the app was updated or on first boot to migrate previously existing rotating
 * wallpaper file to device protected storage so it can be read in direct-boot.
 * This is basically a no-op if there's no file in
 * {@link NoBackupImageWallpaper#ROTATING_WALLPAPER_FILE_PATH}.
 */
public class RotationWallpaperMoveReceiver extends BroadcastReceiver {

    private static final String TAG = "RotationWallpaperMoveReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // This receiver is a no-op on pre-N Android and should only respond to a
        // MY_PACKAGE_REPLACED intent.
        if (intent.getAction() == null
                || !(intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)
                    || intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
                || !BuildCompat.isAtLeastN()) {
            DiskBasedLogger.e(
                    TAG,
                    "Unexpected action or Android version!",
                    context);
            throw new IllegalStateException(
                    "Unexpected broadcast action or unsupported Android version");
        }

        // This is a no-op if there is no rotating wallpaper file in the files directory.
        if (!context.getFileStreamPath(
                NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH).exists()) {
            return;
        }

        PendingResult broadcastResult = goAsync();
        new Thread(() -> {
            Context appContext = context.getApplicationContext();
            Context deviceProtectedContext = appContext.createDeviceProtectedStorageContext();
            try {
                File movedFile = FileMover.moveFileBetweenContexts(appContext,
                        NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH,
                        deviceProtectedContext,
                        NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);

                if (movedFile != null) {
                    // Notify NoBackupImageWallpaper of the change in case that's the currently
                    // set wallpaper
                    Intent intent1 = new Intent(appContext.getPackageName()
                            + NoBackupImageWallpaper.ACTION_ROTATING_WALLPAPER_CHANGED);
                    // Handled by a runtime-registered receiver in NoBackupImageWallpaper.
                    intent1.setPackage(appContext.getPackageName());
                    appContext.sendBroadcast(intent1);
                }
            } catch (IOException e) {
                DiskBasedLogger.e(
                        TAG,
                        "Failed to move rotating wallpaper file to device protected storage: "
                                + e.getMessage(),
                        appContext);
            } finally {
                broadcastResult.finish();
            }
        }).start();

    }
}
