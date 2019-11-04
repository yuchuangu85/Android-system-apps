package com.android.tv.tuner.tvinput.factory;

import android.content.Context;
import android.media.tv.TvInputService.Session;
import com.android.tv.tuner.tvinput.datamanager.ChannelDataManager;

/** {@link android.media.tv.TvInputService.Session} factory */
public interface TunerSessionFactory {

    /** Called when a session is released */
    interface SessionReleasedCallback {

        /**
         * Called when the given session is released.
         *
         * @param session The session that has been released.
         */
        void onReleased(Session session);
    }

    Session create(
            Context context,
            ChannelDataManager channelDataManager,
            SessionReleasedCallback releasedCallback);
}
