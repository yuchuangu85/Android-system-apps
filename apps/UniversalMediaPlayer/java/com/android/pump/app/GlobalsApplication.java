package com.android.pump.app;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;

import com.android.pump.concurrent.Executors;
import com.android.pump.db.DataProvider;
import com.android.pump.db.MediaDb;
import com.android.pump.provider.KnowledgeGraph;
import com.android.pump.ui.CustomRecycledViewPool;
import com.android.pump.util.Globals;
import com.android.pump.util.ImageLoader;

import java.util.concurrent.Executor;

@UiThread
public abstract class GlobalsApplication extends Application implements Globals.Provider {
    private Executor mExecutor;
    private ImageLoader mImageLoader;
    private RecycledViewPool mRecycledViewPool;
    private MediaDb mMediaDb;

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // TODO(b/123038906) Implement
    }

    @Override
    public @NonNull ImageLoader getImageLoader() {
        if (mImageLoader == null) {
            mImageLoader = new ImageLoader(getExecutor());
        }
        return mImageLoader;
    }

    @Override
    public @NonNull RecycledViewPool getRecycledViewPool() {
        if (mRecycledViewPool == null) {
            mRecycledViewPool = new CustomRecycledViewPool();
        }
        return mRecycledViewPool;
    }

    @Override
    public @NonNull MediaDb getMediaDb() {
        if (mMediaDb == null) {
            mMediaDb = new MediaDb(getContentResolver(), getDataProvider(), getExecutor());
            // TODO When can we release mMediaDb?
        }
        return mMediaDb;
    }

    private @NonNull Executor getExecutor() {
        if (mExecutor == null) {
            // TODO Adjust pool size
            mExecutor = Executors.newFixedUniqueThreadPool(
                    Runtime.getRuntime().availableProcessors() * 2 + 1);
        }
        return mExecutor;
    }

    private @NonNull DataProvider getDataProvider() {
        return KnowledgeGraph.getInstance();
    }
}
