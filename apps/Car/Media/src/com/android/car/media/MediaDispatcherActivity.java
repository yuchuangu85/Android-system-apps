package com.android.car.media;

import android.car.Car;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;


/**
 * A trampoline activity that handles the {@link Car#CAR_INTENT_ACTION_MEDIA_TEMPLATE} implicit
 * intent, and fires up either the Media Center's {@link MediaActivity}, or the specialized
 * application if the selected media source is custom (e.g. the Radio app).
 */
public class MediaDispatcherActivity extends FragmentActivity {

    private static final String TAG = "MediaDispatcherActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;

        MediaSourceViewModel mediaSrcVM = MediaSourceViewModel.get(getApplication());
        MediaSource mediaSrc = null;

        if (Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE.equals(action)) {
            String componentName = intent.getStringExtra(Car.CAR_EXTRA_MEDIA_COMPONENT);
            if (componentName != null) {
                ComponentName component = ComponentName.unflattenFromString(componentName);
                mediaSrc = MediaSource.create(this, component);
                if (mediaSrc != null) {
                    mediaSrcVM.setPrimaryMediaSource(mediaSrc);
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "onCreate componentName : " + componentName);
                    }
                }
            }
        }

        if (mediaSrc == null) {
            mediaSrc = mediaSrcVM.getPrimaryMediaSource().getValue();
        }

        Intent newIntent = null;
        if (mediaSrc != null && mediaSrc.isCustom()) {
            // Launch custom app (e.g. Radio)
            String srcPackage = mediaSrc.getPackageName();
            newIntent = getPackageManager().getLaunchIntentForPackage(srcPackage);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Getting launch intent for package : " + srcPackage + (newIntent != null
                        ? " succeeded" : " failed"));
            }
        }
        if (newIntent == null) {
            // Launch media center
            newIntent = new Intent(this, MediaActivity.class);
        }

        newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(newIntent);
        finish();
    }
}
