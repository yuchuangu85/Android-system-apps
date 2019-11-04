package com.android.customization.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

public abstract class ResourcesApkProvider {
    private static final String TAG = "ResourcesApkProvider";

    protected final Context mContext;
    protected final String mStubPackageName;
    protected final Resources mStubApkResources;

    public ResourcesApkProvider(Context context, String stubPackageName) {
        mContext = context;
        mStubPackageName = stubPackageName;
        if (TextUtils.isEmpty(mStubPackageName)) {
            mStubApkResources = null;
        } else {
            Resources apkResources = null;
            try {
                PackageManager pm = mContext.getPackageManager();
                ApplicationInfo stubAppInfo = pm.getApplicationInfo(mStubPackageName,
                        PackageManager.GET_META_DATA | PackageManager.MATCH_SYSTEM_ONLY);
                if (stubAppInfo != null) {
                    apkResources = pm.getResourcesForApplication(stubAppInfo);
                }
            } catch (NameNotFoundException e) {
                Log.w(TAG, String.format("Stub APK for %s not found.", mStubPackageName));
            } finally {
                mStubApkResources = apkResources;
            }
        }
    }

    protected String[] getItemsFromStub(String arrayName) {
        int themesListResId = mStubApkResources.getIdentifier(arrayName, "array",  mStubPackageName);
        return mStubApkResources.getStringArray(themesListResId);
    }

    protected String getItemStringFromStub(String prefix, String itemName) {
        int resourceId = mStubApkResources.getIdentifier(String.format("%s%s", prefix, itemName),
                "string", mStubPackageName);
        return mStubApkResources.getString(resourceId);
    }

    protected Drawable getItemDrawableFromStub(String prefix, String itemName) {
        int resourceId = mStubApkResources.getIdentifier(String.format("%s%s", prefix, itemName),
                "drawable", mStubPackageName);
        return mStubApkResources.getDrawable(resourceId, null);
    }

    public boolean isAvailable() {
        return mStubApkResources != null;
    }
}
