package com.android.wallpaper.asset;

import com.android.wallpaper.compat.BuildCompat;

import java.io.IOException;
import java.io.InputStream;

import androidx.exifinterface.media.ExifInterface;

/**
 * Provides access to basic ExifInterface APIs using {@link android.media.ExifInterface} in OMR1+
 * SDK or SupportLibrary's {@link ExifInterface} for earlier SDK versions.
 */
class ExifInterfaceCompat {

    public static final String TAG_ORIENTATION = ExifInterface.TAG_ORIENTATION;
    public static final int EXIF_ORIENTATION_NORMAL = 1;
    public static final int EXIF_ORIENTATION_UNKNOWN = -1;

    private ExifInterface mSupportExifInterface;
    private android.media.ExifInterface mFrameworkExifInterface;

    /**
     * Reads Exif tags from the specified image input stream. It's the caller's responsibility to
     * close the given InputStream after use.
     * @see ExifInterface#ExifInterface(InputStream)
     * @see android.media.ExifInterface#ExifInterface(InputStream)
     */
    public ExifInterfaceCompat(InputStream inputStream) throws IOException {
        // O-MR1 added support for more formats (HEIF), which Support Library cannot implement,
        // so use the framework version for SDK 27+
        if (BuildCompat.isAtLeastOMR1()) {
            mFrameworkExifInterface = new android.media.ExifInterface(inputStream);
        } else {
            mSupportExifInterface = new ExifInterface(inputStream);
        }
    }

    public int getAttributeInt(String tag, int defaultValue) {
        return mFrameworkExifInterface != null
                ? mFrameworkExifInterface.getAttributeInt(tag, defaultValue)
                : mSupportExifInterface.getAttributeInt(tag, defaultValue);
    }

    public String getAttribute(String tag) {
        return mFrameworkExifInterface != null
                ? mFrameworkExifInterface.getAttribute(tag)
                : mSupportExifInterface.getAttribute(tag);
    }
}
