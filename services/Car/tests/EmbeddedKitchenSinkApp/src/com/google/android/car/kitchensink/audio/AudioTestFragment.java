/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink.audio;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarAppFocusManager.OnAppFocusChangedListener;
import android.car.CarAppFocusManager.OnAppFocusOwnershipCallback;
import android.car.media.CarAudioManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.HwAudioSource;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.CarEmulator;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioTestFragment extends Fragment {
    private static final String TAG = "CAR.AUDIO.KS";
    private static final boolean DBG = true;

    private AudioManager mAudioManager;
    private FocusHandler mAudioFocusHandler;
    private ToggleButton mEnableMocking;

    private AudioPlayer mMusicPlayer;
    private AudioPlayer mMusicPlayerShort;
    private AudioPlayer mNavGuidancePlayer;
    private AudioPlayer mVrPlayer;
    private AudioPlayer mSystemPlayer;
    private AudioPlayer mWavPlayer;
    private AudioPlayer mMusicPlayerForSelectedDisplay;
    private HwAudioSource mHwAudioSource;
    private AudioPlayer[] mAllPlayers;

    private Handler mHandler;
    private Context mContext;

    private Car mCar;
    private CarAppFocusManager mAppFocusManager;
    private AudioAttributes mMusicAudioAttrib;
    private AudioAttributes mNavAudioAttrib;
    private AudioAttributes mVrAudioAttrib;
    private AudioAttributes mRadioAudioAttrib;
    private AudioAttributes mSystemSoundAudioAttrib;
    private AudioAttributes mMusicAudioAttribForDisplay;
    private CarEmulator mCarEmulator;
    private CarAudioManager mCarAudioManager;
    private Spinner mZoneSpinner;
    ArrayAdapter<Integer> mZoneAdapter;
    private Spinner mDisplaySpinner;
    ArrayAdapter<Integer> mDisplayAdapter;
    private LinearLayout mDisplayLayout;
    private int mOldZonePosition;

    private static int sDefaultExtraTestScreenPortId = 1;

    private final AudioManager.OnAudioFocusChangeListener mNavFocusListener = (focusChange) -> {
        Log.i(TAG, "Nav focus change:" + focusChange);
    };
    private final AudioManager.OnAudioFocusChangeListener mVrFocusListener = (focusChange) -> {
        Log.i(TAG, "VR focus change:" + focusChange);
    };
    private final AudioManager.OnAudioFocusChangeListener mRadioFocusListener = (focusChange) -> {
        Log.i(TAG, "Radio focus change:" + focusChange);
    };

    private final CarAppFocusManager.OnAppFocusOwnershipCallback mOwnershipCallbacks =
            new OnAppFocusOwnershipCallback() {
                @Override
                public void onAppFocusOwnershipLost(int focus) {
                }
                @Override
                public void onAppFocusOwnershipGranted(int focus) {
                }
    };

    private void connectCar() {
        mContext = getContext();
        mHandler = new Handler(Looper.getMainLooper());
        mCar = Car.createCar(mContext, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mAppFocusManager =
                        (CarAppFocusManager) mCar.getCarManager(Car.APP_FOCUS_SERVICE);
                OnAppFocusChangedListener listener = new OnAppFocusChangedListener() {
                    @Override
                    public void onAppFocusChanged(int appType, boolean active) {
                    }
                };
                mAppFocusManager.addFocusListener(listener,
                        CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
                mAppFocusManager.addFocusListener(listener,
                        CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);

                mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);

                //take care of zone selection
                int[] zoneList = mCarAudioManager.getAudioZoneIds();
                Integer[] zoneArray = Arrays.stream(zoneList).boxed().toArray(Integer[]::new);
                mZoneAdapter = new ArrayAdapter<>(mContext,
                        android.R.layout.simple_spinner_item, zoneArray);
                mZoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mZoneSpinner.setAdapter(mZoneAdapter);
                mZoneSpinner.setEnabled(true);

                if (mCarAudioManager.isDynamicRoutingEnabled()) {
                    setUpDisplayPlayer();
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
            });
        mCar.connect();
    }

    private void initializePlayers() {
        mMusicAudioAttrib = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();
        mNavAudioAttrib = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .build();
        mVrAudioAttrib = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .build();
        mRadioAudioAttrib = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();
        mSystemSoundAudioAttrib = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();
        // Create a display audio attribute
        mMusicAudioAttribForDisplay = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();


        mMusicPlayerForSelectedDisplay = new AudioPlayer(mContext, R.raw.well_worth_the_wait,
                mMusicAudioAttribForDisplay);
        mMusicPlayer = new AudioPlayer(mContext, R.raw.well_worth_the_wait,
            mMusicAudioAttrib);
        mMusicPlayerShort = new AudioPlayer(mContext, R.raw.ring_classic_01,
            mMusicAudioAttrib);
        mNavGuidancePlayer = new AudioPlayer(mContext, R.raw.turnright,
            mNavAudioAttrib);
        mVrPlayer = new AudioPlayer(mContext, R.raw.one2six,
            mVrAudioAttrib);
        mSystemPlayer = new AudioPlayer(mContext, R.raw.ring_classic_01,
            mSystemSoundAudioAttrib);
        mWavPlayer = new AudioPlayer(mContext, R.raw.free_flight,
            mMusicAudioAttrib);
        final AudioDeviceInfo tuner = findTunerDevice(mContext);
        if (tuner != null) {
            mHwAudioSource = new HwAudioSource.Builder()
                .setAudioAttributes(mMusicAudioAttrib)
                .setAudioDeviceInfo(findTunerDevice(mContext))
                .build();
        }
        mAllPlayers = new AudioPlayer[] {
            mMusicPlayer,
            mMusicPlayerShort,
            mNavGuidancePlayer,
            mVrPlayer,
            mSystemPlayer,
            mWavPlayer
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        connectCar();
        initializePlayers();
        View view = inflater.inflate(R.layout.audio, container, false);
        mAudioManager = (AudioManager) mContext.getSystemService(
                Context.AUDIO_SERVICE);
        mAudioFocusHandler = new FocusHandler(
                view.findViewById(R.id.button_focus_request_selection),
                view.findViewById(R.id.button_audio_focus_request),
                view.findViewById(R.id.text_audio_focus_state));
        view.findViewById(R.id.button_media_play_start).setOnClickListener(v -> {
            boolean requestFocus = true;
            boolean repeat = true;
            mMusicPlayer.start(requestFocus, repeat, AudioManager.AUDIOFOCUS_GAIN);
        });
        view.findViewById(R.id.button_media_play_once).setOnClickListener(v -> {
            mMusicPlayerShort.start(true, false, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            // play only for 1 sec and stop
            mHandler.postDelayed(() -> mMusicPlayerShort.stop(), 1000);
        });
        view.findViewById(R.id.button_media_play_stop).setOnClickListener(v -> mMusicPlayer.stop());
        view.findViewById(R.id.button_wav_play_start).setOnClickListener(
                v -> mWavPlayer.start(true, true, AudioManager.AUDIOFOCUS_GAIN));
        view.findViewById(R.id.button_wav_play_stop).setOnClickListener(v -> mWavPlayer.stop());
        view.findViewById(R.id.button_nav_play_once).setOnClickListener(v -> {
            if (mAppFocusManager == null) {
                Log.e(TAG, "mAppFocusManager is null");
                return;
            }
            if (DBG) {
                Log.i(TAG, "Nav start");
            }
            mAppFocusManager.requestAppFocus(
                    CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, mOwnershipCallbacks);
            if (!mNavGuidancePlayer.isPlaying()) {
                mNavGuidancePlayer.start(true, false,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        () -> mAppFocusManager.abandonAppFocus(mOwnershipCallbacks,
                                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
            }
        });
        view.findViewById(R.id.button_vr_play_once).setOnClickListener(v -> {
            if (mAppFocusManager == null) {
                Log.e(TAG, "mAppFocusManager is null");
                return;
            }
            if (DBG) {
                Log.i(TAG, "VR start");
            }
            mAppFocusManager.requestAppFocus(
                    CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, mOwnershipCallbacks);
            if (!mVrPlayer.isPlaying()) {
                mVrPlayer.start(true, false,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                        () -> mAppFocusManager.abandonAppFocus(mOwnershipCallbacks,
                                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
            }
        });
        view.findViewById(R.id.button_system_play_once).setOnClickListener(v -> {
            if (DBG) {
                Log.i(TAG, "System start");
            }
            if (!mSystemPlayer.isPlaying()) {
                // system sound played without focus
                mSystemPlayer.start(false, false, 0);
            }
        });
        view.findViewById(R.id.button_nav_start).setOnClickListener(v -> handleNavStart());
        view.findViewById(R.id.button_nav_end).setOnClickListener(v -> handleNavEnd());
        view.findViewById(R.id.button_vr_start).setOnClickListener(v -> handleVrStart());
        view.findViewById(R.id.button_vr_end).setOnClickListener(v -> handleVrEnd());
        view.findViewById(R.id.button_radio_start).setOnClickListener(v -> handleRadioStart());
        view.findViewById(R.id.button_radio_end).setOnClickListener(v -> handleRadioEnd());
        view.findViewById(R.id.button_speaker_phone_on).setOnClickListener(
                v -> mAudioManager.setSpeakerphoneOn(true));
        view.findViewById(R.id.button_speaker_phone_off).setOnClickListener(
                v -> mAudioManager.setSpeakerphoneOn(false));
        view.findViewById(R.id.button_microphone_on).setOnClickListener(
                v -> mAudioManager.setMicrophoneMute(false));
        view.findViewById(R.id.button_microphone_off).setOnClickListener(
                v -> mAudioManager.setMicrophoneMute(true));
        final View hwAudioSourceNotFound = view.findViewById(R.id.hw_audio_source_not_found);
        final View hwAudioSourceStart = view.findViewById(R.id.hw_audio_source_start);
        final View hwAudioSourceStop = view.findViewById(R.id.hw_audio_source_stop);
        if (mHwAudioSource == null) {
            hwAudioSourceNotFound.setVisibility(View.VISIBLE);
            hwAudioSourceStart.setVisibility(View.GONE);
            hwAudioSourceStop.setVisibility(View.GONE);
        } else {
            hwAudioSourceNotFound.setVisibility(View.GONE);
            hwAudioSourceStart.setVisibility(View.VISIBLE);
            hwAudioSourceStop.setVisibility(View.VISIBLE);
            view.findViewById(R.id.hw_audio_source_start).setOnClickListener(
                    v -> handleHwAudioSourceStart());
            view.findViewById(R.id.hw_audio_source_stop).setOnClickListener(
                    v -> handleHwAudioSourceStop());
        }

        mEnableMocking = view.findViewById(R.id.button_mock_audio);
        mEnableMocking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mCarEmulator == null) {
                //TODO(pavelm): need to do a full switch between emulated and normal mode
                // all Car*Manager references should be invalidated.
                Toast.makeText(AudioTestFragment.this.getContext(),
                        "Not supported yet :(", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isChecked) {
                mCarEmulator.start();
            } else {
                mCarEmulator.stop();
                mCarEmulator = null;
            }
        });

        //Zone Spinner
        mZoneSpinner = view.findViewById(R.id.zone_spinner);
        mZoneSpinner.setEnabled(false);
        mZoneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleZoneSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        mDisplayLayout = view.findViewById(R.id.audio_display_layout);

        mDisplaySpinner = view.findViewById(R.id.display_spinner);
        mDisplaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleDisplaySelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Manage buttons for audio player for displays
        view.findViewById(R.id.button_display_media_play_start).setOnClickListener(v -> {
            startDisplayAudio();
        });
        view.findViewById(R.id.button_display_media_play_once).setOnClickListener(v -> {
            startDisplayAudio();
            // play only for 1 sec and stop
            mHandler.postDelayed(() -> mMusicPlayerForSelectedDisplay.stop(), 1000);
        });
        view.findViewById(R.id.button_display_media_play_stop)
                .setOnClickListener(v -> mMusicPlayerForSelectedDisplay.stop());

        return view;
    }

    public void handleZoneSelection() {
        int position = mZoneSpinner.getSelectedItemPosition();
        int zone = mZoneAdapter.getItem(position);
        Log.d(TAG, "Zone Selected: " + zone);
        try {
            ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(
                    mContext.getPackageName(), 0);
            int uid = info.uid;
            Log.d(TAG, "handleZoneSelection App uid: " + uid);
            if (mCarAudioManager.setZoneIdForUid(zone, uid)) {
                Log.d(TAG, "Changed uid " + uid + " sound to zone " + zone);
                mOldZonePosition = position;
            } else {
                Log.d(TAG, "Filed to changed uid " + uid + " sound to zone " + zone);
                mZoneSpinner.setSelection(mOldZonePosition);
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "handleZoneSelection Failed to find name: " , e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.i(TAG, "onDestroyView");
        if (mCarEmulator != null) {
            mCarEmulator.stop();
        }
        for (AudioPlayer p : mAllPlayers) {
            p.stop();
        }
        handleHwAudioSourceStop();
        if (mAudioFocusHandler != null) {
            mAudioFocusHandler.release();
            mAudioFocusHandler = null;
        }
        if (mAppFocusManager != null) {
            mAppFocusManager.abandonAppFocus(mOwnershipCallbacks);
        }
    }

    private void handleNavStart() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "Nav start");
        }
        mAppFocusManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
                mOwnershipCallbacks);
        mAudioManager.requestAudioFocus(mNavFocusListener, mNavAudioAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, 0);
    }

    private void handleNavEnd() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "Nav end");
        }
        mAudioManager.abandonAudioFocus(mNavFocusListener, mNavAudioAttrib);
        mAppFocusManager.abandonAppFocus(mOwnershipCallbacks,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
    }

    private AudioDeviceInfo findTunerDevice(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_FM_TUNER) {
                return device;
            }
        }
        return null;
    }

    private void handleHwAudioSourceStart() {
        if (mHwAudioSource != null) {
            mHwAudioSource.start();
        }
    }

    private void handleHwAudioSourceStop() {
        if (mHwAudioSource != null) {
            mHwAudioSource.stop();
        }
    }

    private void handleVrStart() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "VR start");
        }
        mAppFocusManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND,
                mOwnershipCallbacks);
        mAudioManager.requestAudioFocus(mVrFocusListener, mVrAudioAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, 0);
    }

    private void handleVrEnd() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "VR end");
        }
        mAudioManager.abandonAudioFocus(mVrFocusListener, mVrAudioAttrib);
        mAppFocusManager.abandonAppFocus(mOwnershipCallbacks,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
    }

    private void handleRadioStart() {
        if (DBG) {
            Log.i(TAG, "Radio start");
        }
        mAudioManager.requestAudioFocus(mRadioFocusListener, mRadioAudioAttrib,
                AudioManager.AUDIOFOCUS_GAIN, 0);
    }

    private void handleRadioEnd() {
        if (DBG) {
            Log.i(TAG, "Radio end");
        }
        mAudioManager.abandonAudioFocus(mRadioFocusListener, mRadioAudioAttrib);
    }

    private void setUpDisplayPlayer() {
        DisplayManager displayManager =  (DisplayManager) mContext.getSystemService(
                Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        List<Integer> displayList = new ArrayList<>();
        for (Display display : displays) {
            DisplayAddress.Physical physical = (DisplayAddress.Physical) display.getAddress();
            if (physical != null) {
                displayList.add((int) physical.getPort());
                Log.d(TAG, "Found Display Port " + physical.getPort());
            } else {
                Log.d(TAG, "Found Display with no physical " + display.getDisplayId());
            }
        }
        // If only one display is available add another display for testing
        if (displayList.size() == 1) {
            displayList.add(sDefaultExtraTestScreenPortId);
        }

        //take care of display selection
        Integer[] displayArray = displayList.stream().toArray(Integer[]::new);
        mDisplayAdapter = new ArrayAdapter<>(mContext,
                android.R.layout.simple_spinner_item, displayArray);
        mDisplayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mDisplaySpinner.setAdapter(mDisplayAdapter);
        createDisplayAudioPlayer();
    }

    private void createDisplayAudioPlayer() {
        byte selectedDisplayPortId = mDisplayAdapter.getItem(
                mDisplaySpinner.getSelectedItemPosition()).byteValue();
        int zoneIdForDisplayId = mCarAudioManager.getZoneIdForDisplayPortId(selectedDisplayPortId);
        Log.d(TAG, "Setting Bundle to zone " + zoneIdForDisplayId);
        Bundle bundle = new Bundle();
        bundle.putInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID,
                zoneIdForDisplayId);
        mMusicAudioAttribForDisplay = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .addBundle(bundle)
                .build();

        mMusicPlayerForSelectedDisplay = new AudioPlayer(mContext,
                R.raw.well_worth_the_wait,
                mMusicAudioAttribForDisplay);

        mDisplayLayout.findViewById(R.id.audio_display_layout)
                .setVisibility(View.VISIBLE);
    }

    private void startDisplayAudio() {
        byte selectedDisplayPortId = mDisplayAdapter.getItem(
                mDisplaySpinner.getSelectedItemPosition()).byteValue();
        int zoneIdForDisplayId = mCarAudioManager.getZoneIdForDisplayPortId(selectedDisplayPortId);
        Log.d(TAG, "Starting display audio in zone " + zoneIdForDisplayId);
        // Direct audio to the correct source
        // TODO: Figure out a way to facilitate this for the user
        // Currently there is no way of distinguishing apps from the same package to different zones
        // One suggested way would be to create a unique id for each focus requester that is also
        // share with the audio router
        if (zoneIdForDisplayId == CarAudioManager.PRIMARY_AUDIO_ZONE) {
            mMusicPlayerForSelectedDisplay.start(true, false,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        } else {
            // Route everything else to rear seat
            mMusicPlayerForSelectedDisplay.start(true, false,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, "bus100_rear_seat");
        }
    }

    public void handleDisplaySelection() {
        if (mMusicPlayerForSelectedDisplay != null && mMusicPlayerForSelectedDisplay.isPlaying()) {
            mMusicPlayerForSelectedDisplay.stop();
        }
        createDisplayAudioPlayer();
    }


    private class FocusHandler {
        private static final String AUDIO_FOCUS_STATE_GAIN = "gain";
        private static final String AUDIO_FOCUS_STATE_RELEASED_UNKNOWN = "released / unknown";

        private final RadioGroup mRequestSelection;
        private final TextView mText;
        private final AudioFocusListener mFocusListener;
        private AudioFocusRequest mFocusRequest;

        public FocusHandler(RadioGroup radioGroup, Button requestButton, TextView text) {
            mText = text;
            mRequestSelection = radioGroup;
            mRequestSelection.check(R.id.focus_gain);
            setFocusText(AUDIO_FOCUS_STATE_RELEASED_UNKNOWN);
            mFocusListener = new AudioFocusListener();
            requestButton.setOnClickListener(v -> {
                int selectedButtonId = mRequestSelection.getCheckedRadioButtonId();
                int focusRequest;
                switch (selectedButtonId) {
                    case R.id.focus_gain:
                        focusRequest = AudioManager.AUDIOFOCUS_GAIN;
                        break;
                    case R.id.focus_gain_transient:
                        focusRequest = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
                        break;
                    case R.id.focus_gain_transient_duck:
                        focusRequest = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
                        break;
                    case R.id.focus_gain_transient_exclusive:
                        focusRequest = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
                        break;
                    case R.id.focus_release:
                    default:
                        abandonAudioFocus();
                        return;
                }
                mFocusRequest = new AudioFocusRequest.Builder(focusRequest)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build())
                        .setOnAudioFocusChangeListener(mFocusListener)
                        .build();
                int ret = mAudioManager.requestAudioFocus(mFocusRequest);
                Log.i(TAG, "requestAudioFocus returned " + ret);
                if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    setFocusText(AUDIO_FOCUS_STATE_GAIN);
                }
            });
        }

        public void release() {
            abandonAudioFocus();
        }

        private void abandonAudioFocus() {
            if (DBG) {
                Log.i(TAG, "abandonAudioFocus");
            }
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
            mFocusRequest = null;
            setFocusText(AUDIO_FOCUS_STATE_RELEASED_UNKNOWN);
        }

        private void setFocusText(String msg) {
            mText.setText("focus state:" + msg);
        }

        private class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.i(TAG, "onAudioFocusChange " + focusChange);
                if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    setFocusText(AUDIO_FOCUS_STATE_GAIN);
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    setFocusText("loss");
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    setFocusText("loss,transient");
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    setFocusText("loss,transient,duck");
                }
            }
        }
    }
}
