package com.android.customization.model.clock;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.clock.Clockface.Builder;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.ContentUriAsset;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

public class ContentProviderClockProvider implements ClockProvider {

    private final Context mContext;
    private final ProviderInfo mProviderInfo;
    private List<Clockface> mClocks;

    public ContentProviderClockProvider(Context context) {
        mContext = context;
        String providerAuthority = mContext.getString(R.string.clocks_provider_authority);
        // TODO: check permissions if needed
        mProviderInfo = TextUtils.isEmpty(providerAuthority) ? null
                : mContext.getPackageManager().resolveContentProvider(providerAuthority,
                        PackageManager.MATCH_SYSTEM_ONLY);
    }

    @Override
    public boolean isAvailable() {
        return mProviderInfo != null && (mClocks == null || !mClocks.isEmpty());
    }

    @Override
    public void fetch(OptionsFetchedListener<Clockface> callback, boolean reload) {
        if (!isAvailable()) {
            if (callback != null) {
                callback.onError(null);
            }
            return;
        }
        if (mClocks != null && !reload) {
            if (callback != null) {
                if (!mClocks.isEmpty()) {
                    callback.onOptionsLoaded(mClocks);
                } else {
                    callback.onError(null);
                }
            }
            return;
        }
        new ClocksFetchTask(mContext, mProviderInfo, options -> {
            mClocks = options;
            if (callback != null) {
                if (!mClocks.isEmpty()) {
                    callback.onOptionsLoaded(mClocks);
                } else {
                    callback.onError(null);
                }
            }
        }).execute();
    }

    private static class ClocksFetchTask extends AsyncTask<Void, Void, List<Clockface>> {

        private static final String LIST_OPTIONS = "list_options";

        private static final String COL_NAME = "name";
        private static final String COL_TITLE = "title";
        private static final String COL_ID = "id";
        private static final String COL_THUMBNAIL = "thumbnail";
        private static final String COL_PREVIEW = "preview";

        private final OptionsFetchedListener<Clockface> mCallback;
        private Context mContext;
        private final ProviderInfo mProviderInfo;

        public ClocksFetchTask(Context context, ProviderInfo providerInfo,
                OptionsFetchedListener<Clockface> callback) {
            super();
            mContext = context;
            mProviderInfo = providerInfo;
            mCallback = callback;
        }

        @Override
        protected List<Clockface> doInBackground(Void... voids) {
            Uri optionsUri = new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(mProviderInfo.authority)
                    .appendPath(LIST_OPTIONS)
                    .build();

            ContentResolver resolver = mContext.getContentResolver();

            List<Clockface> clockfaces = new ArrayList<>();
            try (Cursor c = resolver.query(optionsUri, null, null, null, null)) {
                while(c.moveToNext()) {
                    String id = c.getString(c.getColumnIndex(COL_ID));
                    String title = c.getString(c.getColumnIndex(COL_TITLE));
                    String thumbnailUri = c.getString(c.getColumnIndex(COL_THUMBNAIL));
                    String previewUri = c.getString(c.getColumnIndex(COL_PREVIEW));
                    Uri thumbnail = Uri.parse(thumbnailUri);
                    Uri preview = Uri.parse(previewUri);

                    Clockface.Builder builder = new Builder();
                    builder.setId(id).setTitle(title)
                            .setThumbnail(new ContentUriAsset(mContext, thumbnail,
                                    RequestOptions.fitCenterTransform()))
                            .setPreview(new ContentUriAsset(mContext, preview,
                                    RequestOptions.fitCenterTransform()));
                    clockfaces.add(builder.build());
                }
                Glide.get(mContext).clearDiskCache();
            } catch (Exception e) {
                clockfaces = null;
            } finally {
                mContext = null;
            }
            return clockfaces;
        }

        @Override
        protected void onPostExecute(List<Clockface> clockfaces) {
            super.onPostExecute(clockfaces);
            mCallback.onOptionsLoaded(clockfaces);
        }
    }
}
