package com.android.car.media.testmediaapp.phone;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.android.car.media.testmediaapp.TmaBrowser;
import com.android.car.media.testmediaapp.prefs.TmaPrefsActivity;
import com.android.car.media.testmediaapp.R;

/**
 * Runs on a phone, thus making the browse tree available to bluetooth.
 * TODO: the fake playback doesn't work over BT, might need to send some real bytes...
 */
public class TmaLauncherActivity extends AppCompatActivity {

    private static final String TAG = "TmaLauncherActivity";

    private MediaBrowserCompat mediaBrowser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tma_launcher_activity);

        findViewById(R.id.prefs_button).setOnClickListener(v -> {
            Intent prefsIntent = new Intent();
            prefsIntent.setClass(TmaLauncherActivity.this, TmaPrefsActivity.class);
            prefsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            prefsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(prefsIntent);
        });


        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, TmaBrowser.class),
                mConnectionCallbacks, null);
    }

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    try {
                        // Get the token for the MediaSession
                        MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

                        // Create a MediaControllerCompat
                        MediaControllerCompat controller =
                                new MediaControllerCompat(TmaLauncherActivity.this, token);

                        // Save the controller
                        MediaControllerCompat.setMediaController(
                                TmaLauncherActivity.this, controller);
                    } catch (RemoteException e) {
                        Log.e(TAG, "onConnected failed: " + e);
                    }
                }
            };

    @Override
    public void onStart() {
        super.onStart();
        mediaBrowser.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mediaBrowser.disconnect();
    }
}
