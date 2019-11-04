package com.android.customization.picker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;

/**
 * BroadcastReceiver that can notify a listener when the system time (minutes) changes.
 * Use {@link #registerNewReceiver(Context, TimeListener)} to create a new instance that will be
 * automatically registered using the given Context.
 */
public class TimeTicker extends BroadcastReceiver {

    public interface TimeListener {
        void onCurrentTimeChanged();
    }

    public static TimeTicker registerNewReceiver(Context context, TimeListener listener) {
        TimeTicker receiver = new TimeTicker(listener);
        // Register broadcast receiver for time tick
        final IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        context.registerReceiver(receiver, filter);
        return receiver;
    }

    @Nullable private TimeListener mListener;

    private TimeTicker(TimeListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mListener != null) {
            mListener.onCurrentTimeChanged();
        }
    }
}
