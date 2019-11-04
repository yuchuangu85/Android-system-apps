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

package com.android.tv.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvContentRatingSystemInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.compat.TvInputInfoCompat;
import com.android.tv.common.dagger.annotations.ApplicationContext;
import com.android.tv.common.util.CommonUtils;
import com.android.tv.common.util.SystemProperties;
import com.android.tv.features.TvFeatures;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.util.images.ImageCache;
import com.android.tv.util.images.ImageLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Helper class for {@link TvInputManager}. */
@UiThread
@Singleton
public class TvInputManagerHelper {
    private static final String TAG = "TvInputManagerHelper";
    private static final boolean DEBUG = false;

    public interface TvInputManagerInterface {
        TvInputInfo getTvInputInfo(String inputId);

        Integer getInputState(String inputId);

        void registerCallback(TvInputCallback internalCallback, Handler handler);

        void unregisterCallback(TvInputCallback internalCallback);

        List<TvInputInfo> getTvInputList();

        List<TvContentRatingSystemInfo> getTvContentRatingSystemList();
    }

    private static final class TvInputManagerImpl implements TvInputManagerInterface {
        private final TvInputManager delegate;

        private TvInputManagerImpl(TvInputManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public TvInputInfo getTvInputInfo(String inputId) {
            return delegate.getTvInputInfo(inputId);
        }

        @Override
        public Integer getInputState(String inputId) {
            return delegate.getInputState(inputId);
        }

        @Override
        public void registerCallback(TvInputCallback internalCallback, Handler handler) {
            delegate.registerCallback(internalCallback, handler);
        }

        @Override
        public void unregisterCallback(TvInputCallback internalCallback) {
            delegate.unregisterCallback(internalCallback);
        }

        @Override
        public List<TvInputInfo> getTvInputList() {
            return delegate.getTvInputList();
        }

        @Override
        public List<TvContentRatingSystemInfo> getTvContentRatingSystemList() {
            return delegate.getTvContentRatingSystemList();
        }
    }

    /** Types of HDMI device and bundled tuner. */
    public static final int TYPE_CEC_DEVICE = -2;

    public static final int TYPE_BUNDLED_TUNER = -3;
    public static final int TYPE_CEC_DEVICE_RECORDER = -4;
    public static final int TYPE_CEC_DEVICE_PLAYBACK = -5;
    public static final int TYPE_MHL_MOBILE = -6;

    private static final String PERMISSION_ACCESS_ALL_EPG_DATA =
            "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA";
    private static final String[] mPhysicalTunerBlackList = {
    };
    private static final String META_LABEL_SORT_KEY = "input_sort_key";

    private static final String TV_INPUT_ALLOW_3RD_PARTY_INPUTS = "tv_input_allow_3rd_party_inputs";

    private static final String[] SYSTEM_INPUT_ID_BLACKLIST = {
        "com.google.android.videos/" // Play Movies
    };

    /** The default tv input priority to show. */
    private static final ArrayList<Integer> DEFAULT_TV_INPUT_PRIORITY = new ArrayList<>();

    static {
        DEFAULT_TV_INPUT_PRIORITY.add(TYPE_BUNDLED_TUNER);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_TUNER);
        DEFAULT_TV_INPUT_PRIORITY.add(TYPE_CEC_DEVICE);
        DEFAULT_TV_INPUT_PRIORITY.add(TYPE_CEC_DEVICE_RECORDER);
        DEFAULT_TV_INPUT_PRIORITY.add(TYPE_CEC_DEVICE_PLAYBACK);
        DEFAULT_TV_INPUT_PRIORITY.add(TYPE_MHL_MOBILE);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_HDMI);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_DVI);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_COMPONENT);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_SVIDEO);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_COMPOSITE);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_DISPLAY_PORT);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_VGA);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_SCART);
        DEFAULT_TV_INPUT_PRIORITY.add(TvInputInfo.TYPE_OTHER);
    }

    private static final String[] PARTNER_TUNER_INPUT_PREFIX_BLACKLIST = {
    };

    private static final String[] TESTABLE_INPUTS = {
        "com.android.tv.testinput/.TestTvInputService"
    };

    private final Context mContext;
    private final PackageManager mPackageManager;
    protected final TvInputManagerInterface mTvInputManager;
    private final Map<String, Integer> mInputStateMap = new HashMap<>();
    private final Map<String, TvInputInfoCompat> mInputMap = new HashMap<>();
    private final Map<String, String> mTvInputLabels = new ArrayMap<>();
    private final Map<String, String> mTvInputCustomLabels = new ArrayMap<>();
    private final Map<String, Boolean> mInputIdToPartnerInputMap = new HashMap<>();

    private final Map<String, CharSequence> mTvInputApplicationLabels = new ArrayMap<>();
    private final Map<String, Drawable> mTvInputApplicationIcons = new ArrayMap<>();
    private final Map<String, Drawable> mTvInputApplicationBanners = new ArrayMap<>();

    private final ContentObserver mContentObserver;

    private final TvInputCallback mInternalCallback =
            new TvInputCallback() {
                @Override
                public void onInputStateChanged(String inputId, int state) {
                    if (DEBUG) Log.d(TAG, "onInputStateChanged " + inputId + " state=" + state);
                    TvInputInfo info = mInputMap.get(inputId).getTvInputInfo();
                    if (info == null || isInputBlocked(info)) {
                        return;
                    }
                    mInputStateMap.put(inputId, state);
                    for (TvInputCallback callback : mCallbacks) {
                        callback.onInputStateChanged(inputId, state);
                    }
                }

                @Override
                public void onInputAdded(String inputId) {
                    if (DEBUG) Log.d(TAG, "onInputAdded " + inputId);
                    TvInputInfo info = mTvInputManager.getTvInputInfo(inputId);
                    if (info == null || isInputBlocked(info)) {
                        return;
                    }
                    if (info != null) {
                        mInputMap.put(inputId, new TvInputInfoCompat(mContext, info));
                        CharSequence label = info.loadLabel(mContext);
                        // in tests the label may be missing just use the input id
                        mTvInputLabels.put(inputId, label != null ? label.toString() : inputId);
                        CharSequence inputCustomLabel = info.loadCustomLabel(mContext);
                        if (inputCustomLabel != null) {
                            mTvInputCustomLabels.put(inputId, inputCustomLabel.toString());
                        }
                        mInputStateMap.put(inputId, mTvInputManager.getInputState(inputId));
                        mInputIdToPartnerInputMap.put(inputId, isPartnerInput(info));
                    }
                    mContentRatingsManager.update();
                    for (TvInputCallback callback : mCallbacks) {
                        callback.onInputAdded(inputId);
                    }
                }

                @Override
                public void onInputRemoved(String inputId) {
                    if (DEBUG) Log.d(TAG, "onInputRemoved " + inputId);
                    mInputMap.remove(inputId);
                    mTvInputLabels.remove(inputId);
                    mTvInputCustomLabels.remove(inputId);
                    mTvInputApplicationLabels.remove(inputId);
                    mTvInputApplicationIcons.remove(inputId);
                    mTvInputApplicationBanners.remove(inputId);
                    mInputStateMap.remove(inputId);
                    mInputIdToPartnerInputMap.remove(inputId);
                    mContentRatingsManager.update();
                    for (TvInputCallback callback : mCallbacks) {
                        callback.onInputRemoved(inputId);
                    }
                    ImageCache.getInstance()
                            .remove(ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(inputId));
                }

                @Override
                public void onInputUpdated(String inputId) {
                    if (DEBUG) Log.d(TAG, "onInputUpdated " + inputId);
                    TvInputInfo info = mTvInputManager.getTvInputInfo(inputId);
                    if (info == null || isInputBlocked(info)) {
                        return;
                    }
                    mInputMap.put(inputId, new TvInputInfoCompat(mContext, info));
                    mTvInputLabels.put(inputId, info.loadLabel(mContext).toString());
                    CharSequence inputCustomLabel = info.loadCustomLabel(mContext);
                    if (inputCustomLabel != null) {
                        mTvInputCustomLabels.put(inputId, inputCustomLabel.toString());
                    }
                    mTvInputApplicationLabels.remove(inputId);
                    mTvInputApplicationIcons.remove(inputId);
                    mTvInputApplicationBanners.remove(inputId);
                    for (TvInputCallback callback : mCallbacks) {
                        callback.onInputUpdated(inputId);
                    }
                    ImageCache.getInstance()
                            .remove(ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(inputId));
                }

                @Override
                public void onTvInputInfoUpdated(TvInputInfo inputInfo) {
                    if (DEBUG) Log.d(TAG, "onTvInputInfoUpdated " + inputInfo);
                    if (isInputBlocked(inputInfo)) {
                        return;
                    }
                    mInputMap.put(inputInfo.getId(), new TvInputInfoCompat(mContext, inputInfo));
                    mTvInputLabels.put(inputInfo.getId(), inputInfo.loadLabel(mContext).toString());
                    CharSequence inputCustomLabel = inputInfo.loadCustomLabel(mContext);
                    if (inputCustomLabel != null) {
                        mTvInputCustomLabels.put(inputInfo.getId(), inputCustomLabel.toString());
                    }
                    for (TvInputCallback callback : mCallbacks) {
                        callback.onTvInputInfoUpdated(inputInfo);
                    }
                    ImageCache.getInstance()
                            .remove(
                                    ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(
                                            inputInfo.getId()));
                }
            };

    private final Handler mHandler = new Handler();
    private boolean mStarted;
    private final HashSet<TvInputCallback> mCallbacks = new HashSet<>();
    private final ContentRatingsManager mContentRatingsManager;
    private final ParentalControlSettings mParentalControlSettings;
    private final Comparator<TvInputInfo> mTvInputInfoComparator;
    private boolean mAllow3rdPartyInputs;

    @Inject
    public TvInputManagerHelper(@ApplicationContext Context context) {
        this(context, createTvInputManagerWrapper(context));
    }

    @Nullable
    protected static TvInputManagerImpl createTvInputManagerWrapper(Context context) {
        TvInputManager tvInputManager =
                (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        return tvInputManager == null ? null : new TvInputManagerImpl(tvInputManager);
    }

    @VisibleForTesting
    protected TvInputManagerHelper(
            Context context, @Nullable TvInputManagerInterface tvInputManager) {
        mContext = context.getApplicationContext();
        mPackageManager = context.getPackageManager();
        mTvInputManager = tvInputManager;
        mContentRatingsManager = new ContentRatingsManager(context, tvInputManager);
        mParentalControlSettings = new ParentalControlSettings(context);
        mTvInputInfoComparator = new InputComparatorInternal(this);
        mContentObserver =
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        String option = uri.getLastPathSegment();
                        if (option == null || !option.equals(TV_INPUT_ALLOW_3RD_PARTY_INPUTS)) {
                            return;
                        }
                        boolean previousSetting = mAllow3rdPartyInputs;
                        updateAllow3rdPartyInputs();
                        if (previousSetting == mAllow3rdPartyInputs) {
                            return;
                        }
                        initInputMaps();
                    }
                };
    }

    public void start() {
        if (!hasTvInputManager()) {
            // Not a TV device
            return;
        }
        if (mStarted) {
            return;
        }
        if (DEBUG) Log.d(TAG, "start");
        mStarted = true;
        mContext.getContentResolver()
                .registerContentObserver(
                        Settings.Global.getUriFor(TV_INPUT_ALLOW_3RD_PARTY_INPUTS),
                        true,
                        mContentObserver);
        updateAllow3rdPartyInputs();
        mTvInputManager.registerCallback(mInternalCallback, mHandler);
        initInputMaps();
        mContentRatingsManager.update();
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mTvInputManager.unregisterCallback(mInternalCallback);
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        mStarted = false;
        mInputStateMap.clear();
        mInputMap.clear();
        mTvInputLabels.clear();
        mTvInputCustomLabels.clear();
        mTvInputApplicationLabels.clear();
        mTvInputApplicationIcons.clear();
        mTvInputApplicationBanners.clear();
        mInputIdToPartnerInputMap.clear();
    }

    /** Clears the TvInput labels map. */
    public void clearTvInputLabels() {
        mTvInputLabels.clear();
        mTvInputCustomLabels.clear();
        mTvInputApplicationLabels.clear();
    }

    public List<TvInputInfo> getTvInputInfos(boolean availableOnly, boolean tunerOnly) {
        ArrayList<TvInputInfo> list = new ArrayList<>();
        for (Map.Entry<String, Integer> pair : mInputStateMap.entrySet()) {
            if (availableOnly && pair.getValue() == TvInputManager.INPUT_STATE_DISCONNECTED) {
                continue;
            }
            TvInputInfo input = getTvInputInfo(pair.getKey());
            if (input == null || isInputBlocked(input)) {
                continue;
            }
            if (tunerOnly && input.getType() != TvInputInfo.TYPE_TUNER) {
                continue;
            }
            list.add(input);
        }
        Collections.sort(list, mTvInputInfoComparator);
        return list;
    }

    /**
     * Returns the default comparator for {@link TvInputInfo}. See {@link InputComparatorInternal}
     * for detail.
     */
    public Comparator<TvInputInfo> getDefaultTvInputInfoComparator() {
        return mTvInputInfoComparator;
    }

    /**
     * Checks if the input is from a partner.
     *
     * <p>It's visible for comparator test. Package private is enough for this method, but public is
     * necessary to workaround mockito bug.
     */
    @VisibleForTesting
    public boolean isPartnerInput(TvInputInfo inputInfo) {
        return isSystemInput(inputInfo) && !isBundledInput(inputInfo);
    }

    /** Does the input have {@link ApplicationInfo#FLAG_SYSTEM} set. */
    public boolean isSystemInput(TvInputInfo inputInfo) {
        return inputInfo != null
                && (inputInfo.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0;
    }

    /** Is the input one known bundled inputs not written by OEM/SOCs. */
    public boolean isBundledInput(TvInputInfo inputInfo) {
        return inputInfo != null
                && CommonUtils.isInBundledPackageSet(
                        inputInfo.getServiceInfo().applicationInfo.packageName);
    }

    /**
     * Returns if the given input is bundled and written by OEM/SOCs. This returns the cached
     * result.
     */
    public boolean isPartnerInput(String inputId) {
        Boolean isPartnerInput = mInputIdToPartnerInputMap.get(inputId);
        return (isPartnerInput != null) ? isPartnerInput : false;
    }

    /**
     * Is (Context.TV_INPUT_SERVICE) available.
     *
     * <p>This is only available on TV devices.
     */
    public boolean hasTvInputManager() {
        return mTvInputManager != null;
    }

    /** Loads label of {@code info}. */
    public String loadLabel(TvInputInfo info) {
        String label = mTvInputLabels.get(info.getId());
        if (label == null) {
            label = info.loadLabel(mContext).toString();
            mTvInputLabels.put(info.getId(), label);
        }
        return label;
    }

    /** Loads custom label of {@code info} */
    public String loadCustomLabel(TvInputInfo info) {
        String customLabel = mTvInputCustomLabels.get(info.getId());
        if (customLabel == null) {
            CharSequence customLabelCharSequence = info.loadCustomLabel(mContext);
            if (customLabelCharSequence != null) {
                customLabel = customLabelCharSequence.toString();
                mTvInputCustomLabels.put(info.getId(), customLabel);
            }
        }
        return customLabel;
    }

    /** Gets the tv input application's label. */
    public CharSequence getTvInputApplicationLabel(CharSequence inputId) {
        return mTvInputApplicationLabels.get(inputId);
    }

    /** Stores the tv input application's label. */
    public void setTvInputApplicationLabel(String inputId, CharSequence label) {
        mTvInputApplicationLabels.put(inputId, label);
    }

    /** Gets the tv input application's icon. */
    public Drawable getTvInputApplicationIcon(String inputId) {
        return mTvInputApplicationIcons.get(inputId);
    }

    /** Stores the tv input application's icon. */
    public void setTvInputApplicationIcon(String inputId, Drawable icon) {
        mTvInputApplicationIcons.put(inputId, icon);
    }

    /** Gets the tv input application's banner. */
    public Drawable getTvInputApplicationBanner(String inputId) {
        return mTvInputApplicationBanners.get(inputId);
    }

    /** Stores the tv input application's banner. */
    public void setTvInputApplicationBanner(String inputId, Drawable banner) {
        mTvInputApplicationBanners.put(inputId, banner);
    }

    /** Returns if TV input exists with the input id. */
    public boolean hasTvInputInfo(String inputId) {
        SoftPreconditions.checkState(
                mStarted, TAG, "hasTvInputInfo() called before TvInputManagerHelper was started.");
        return mStarted && !TextUtils.isEmpty(inputId) && mInputMap.get(inputId) != null;
    }

    @Nullable
    public TvInputInfo getTvInputInfo(String inputId) {
        TvInputInfoCompat inputInfo = getTvInputInfoCompat(inputId);
        return inputInfo == null ? null : inputInfo.getTvInputInfo();
    }

    @Nullable
    public TvInputInfoCompat getTvInputInfoCompat(String inputId) {
        SoftPreconditions.checkState(
                mStarted, TAG, "getTvInputInfo() called before TvInputManagerHelper was started.");
        if (!mStarted) {
            return null;
        }
        if (inputId == null) {
            return null;
        }
        return mInputMap.get(inputId);
    }

    public ApplicationInfo getTvInputAppInfo(String inputId) {
        TvInputInfo info = getTvInputInfo(inputId);
        return info == null ? null : info.getServiceInfo().applicationInfo;
    }

    public int getTunerTvInputSize() {
        int size = 0;
        for (TvInputInfoCompat input : mInputMap.values()) {
            if (input.getType() == TvInputInfo.TYPE_TUNER) {
                ++size;
            }
        }
        return size;
    }
    /**
     * Returns TvInputInfo's input state.
     *
     * @param inputInfo
     * @return An Integer which stands for the input state {@link
     *     TvInputManager.INPUT_STATE_DISCONNECTED} if inputInfo is null
     */
    public int getInputState(@Nullable TvInputInfo inputInfo) {
        return inputInfo == null
                ? TvInputManager.INPUT_STATE_DISCONNECTED
                : getInputState(inputInfo.getId());
    }

    public int getInputState(String inputId) {
        SoftPreconditions.checkState(mStarted, TAG, "AvailabilityManager not started");
        if (!mStarted) {
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
        Integer state = mInputStateMap.get(inputId);
        if (state == null) {
            Log.w(TAG, "getInputState: no such input (id=" + inputId + ")");
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
        return state;
    }

    public void addCallback(TvInputCallback callback) {
        mCallbacks.add(callback);
    }

    public void removeCallback(TvInputCallback callback) {
        mCallbacks.remove(callback);
    }

    public ParentalControlSettings getParentalControlSettings() {
        return mParentalControlSettings;
    }

    /** Returns a ContentRatingsManager instance for a given application context. */
    public ContentRatingsManager getContentRatingsManager() {
        return mContentRatingsManager;
    }

    private int getInputSortKey(TvInputInfo input) {
        return input.getServiceInfo().metaData.getInt(META_LABEL_SORT_KEY, Integer.MAX_VALUE);
    }

    private boolean isInputPhysicalTuner(TvInputInfo input) {
        String packageName = input.getServiceInfo().packageName;
        if (Arrays.asList(mPhysicalTunerBlackList).contains(packageName)) {
            return false;
        }

        if (input.createSetupIntent() == null) {
            return false;
        } else {
            boolean mayBeTunerInput =
                    mPackageManager.checkPermission(
                                    PERMISSION_ACCESS_ALL_EPG_DATA,
                                    input.getServiceInfo().packageName)
                            == PackageManager.PERMISSION_GRANTED;
            if (!mayBeTunerInput) {
                try {
                    ApplicationInfo ai =
                            mPackageManager.getApplicationInfo(
                                    input.getServiceInfo().packageName, 0);
                    if ((ai.flags
                                    & (ApplicationInfo.FLAG_SYSTEM
                                            | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
                            == 0) {
                        return false;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isInBlackList(String inputId) {
        if (TvFeatures.USE_PARTNER_INPUT_BLACKLIST.isEnabled(mContext)) {
            for (String disabledTunerInputPrefix : PARTNER_TUNER_INPUT_PREFIX_BLACKLIST) {
                if (inputId.contains(disabledTunerInputPrefix)) {
                    return true;
                }
            }
        }
        if (CommonUtils.isRoboTest()) return false;
        if (CommonUtils.isRunningInTest()) {
            for (String testableInput : TESTABLE_INPUTS) {
                if (testableInput.equals(inputId)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void initInputMaps() {
        mInputMap.clear();
        mTvInputLabels.clear();
        mTvInputCustomLabels.clear();
        mTvInputApplicationLabels.clear();
        mTvInputApplicationIcons.clear();
        mTvInputApplicationBanners.clear();
        mInputStateMap.clear();
        mInputIdToPartnerInputMap.clear();
        for (TvInputInfo input : mTvInputManager.getTvInputList()) {
            if (DEBUG) {
                Log.d(TAG, "Input detected " + input);
            }
            String inputId = input.getId();
            if (isInputBlocked(input)) {
                continue;
            }
            mInputMap.put(inputId, new TvInputInfoCompat(mContext, input));
            int state = mTvInputManager.getInputState(inputId);
            mInputStateMap.put(inputId, state);
            mInputIdToPartnerInputMap.put(inputId, isPartnerInput(input));
        }
        SoftPreconditions.checkState(
                mInputStateMap.size() == mInputMap.size(),
                TAG,
                "mInputStateMap not the same size as mInputMap");
    }

    private void updateAllow3rdPartyInputs() {
        int setting;
        try {
            setting =
                    Settings.Global.getInt(
                            mContext.getContentResolver(), TV_INPUT_ALLOW_3RD_PARTY_INPUTS);
        } catch (SettingNotFoundException e) {
            mAllow3rdPartyInputs = SystemProperties.ALLOW_THIRD_PARTY_INPUTS.getValue();
            return;
        }
        mAllow3rdPartyInputs = setting == 1;
    }

    private boolean isInputBlocked(TvInputInfo info) {
        if (!mAllow3rdPartyInputs) {
            if (!isSystemInput(info)) {
                return true;
            }
            for (String id : SYSTEM_INPUT_ID_BLACKLIST) {
                if (info.getId().startsWith(id)) {
                    return true;
                }
            }
        }
        return isInBlackList(info.getId());
    }

    /**
     * Default comparator for TvInputInfo.
     *
     * <p>It's static class that accepts {@link TvInputManagerHelper} as parameter to test. To test
     * comparator, we need to mock API in parent class such as {@link #isPartnerInput}, but it's
     * impossible for an inner class to use mocked methods. (i.e. Mockito's spy doesn't work)
     */
    @VisibleForTesting
    static class InputComparatorInternal implements Comparator<TvInputInfo> {
        private final TvInputManagerHelper mInputManager;

        public InputComparatorInternal(TvInputManagerHelper inputManager) {
            mInputManager = inputManager;
        }

        @Override
        public int compare(TvInputInfo lhs, TvInputInfo rhs) {
            if (mInputManager.isPartnerInput(lhs) != mInputManager.isPartnerInput(rhs)) {
                return mInputManager.isPartnerInput(lhs) ? -1 : 1;
            }
            return mInputManager.loadLabel(lhs).compareTo(mInputManager.loadLabel(rhs));
        }
    }

    /**
     * A comparator used for {@link com.android.tv.ui.SelectInputView} to show the list of TV
     * inputs.
     */
    public static class HardwareInputComparator implements Comparator<TvInputInfo> {
        private Map<Integer, Integer> mTypePriorities = new HashMap<>();
        private final TvInputManagerHelper mTvInputManagerHelper;
        private final Context mContext;

        public HardwareInputComparator(Context context, TvInputManagerHelper tvInputManagerHelper) {
            mContext = context;
            mTvInputManagerHelper = tvInputManagerHelper;
            setupDeviceTypePriorities();
        }

        @Override
        public int compare(TvInputInfo lhs, TvInputInfo rhs) {
            if (lhs == null) {
                return (rhs == null) ? 0 : 1;
            }
            if (rhs == null) {
                return -1;
            }

            boolean enabledL =
                    (mTvInputManagerHelper.getInputState(lhs)
                            != TvInputManager.INPUT_STATE_DISCONNECTED);
            boolean enabledR =
                    (mTvInputManagerHelper.getInputState(rhs)
                            != TvInputManager.INPUT_STATE_DISCONNECTED);
            if (enabledL != enabledR) {
                return enabledL ? -1 : 1;
            }

            int priorityL = getPriority(lhs);
            int priorityR = getPriority(rhs);
            if (priorityL != priorityR) {
                return priorityL - priorityR;
            }

            if (lhs.getType() == TvInputInfo.TYPE_TUNER
                    && rhs.getType() == TvInputInfo.TYPE_TUNER) {
                boolean isPhysicalL = mTvInputManagerHelper.isInputPhysicalTuner(lhs);
                boolean isPhysicalR = mTvInputManagerHelper.isInputPhysicalTuner(rhs);
                if (isPhysicalL != isPhysicalR) {
                    return isPhysicalL ? -1 : 1;
                }
            }

            int sortKeyL = mTvInputManagerHelper.getInputSortKey(lhs);
            int sortKeyR = mTvInputManagerHelper.getInputSortKey(rhs);
            if (sortKeyL != sortKeyR) {
                return sortKeyR - sortKeyL;
            }

            String parentLabelL =
                    lhs.getParentId() != null
                            ? getLabel(mTvInputManagerHelper.getTvInputInfo(lhs.getParentId()))
                            : getLabel(mTvInputManagerHelper.getTvInputInfo(lhs.getId()));
            String parentLabelR =
                    rhs.getParentId() != null
                            ? getLabel(mTvInputManagerHelper.getTvInputInfo(rhs.getParentId()))
                            : getLabel(mTvInputManagerHelper.getTvInputInfo(rhs.getId()));

            if (!TextUtils.equals(parentLabelL, parentLabelR)) {
                return parentLabelL.compareToIgnoreCase(parentLabelR);
            }
            return getLabel(lhs).compareToIgnoreCase(getLabel(rhs));
        }

        private String getLabel(TvInputInfo input) {
            if (input == null) {
                return "";
            }
            String label = mTvInputManagerHelper.loadCustomLabel(input);
            if (TextUtils.isEmpty(label)) {
                label = mTvInputManagerHelper.loadLabel(input);
            }
            return label;
        }

        private int getPriority(TvInputInfo info) {
            Integer priority = null;
            if (mTypePriorities != null) {
                priority = mTypePriorities.get(getTvInputTypeForPriority(info));
            }
            if (priority != null) {
                return priority;
            }
            return Integer.MAX_VALUE;
        }

        private void setupDeviceTypePriorities() {
            mTypePriorities = Partner.getInstance(mContext).getInputsOrderMap();

            // Fill in any missing priorities in the map we got from the OEM
            int priority = mTypePriorities.size();
            for (int type : DEFAULT_TV_INPUT_PRIORITY) {
                if (!mTypePriorities.containsKey(type)) {
                    mTypePriorities.put(type, priority++);
                }
            }
        }

        private int getTvInputTypeForPriority(TvInputInfo info) {
            if (info.getHdmiDeviceInfo() != null) {
                if (info.getHdmiDeviceInfo().isCecDevice()) {
                    switch (info.getHdmiDeviceInfo().getDeviceType()) {
                        case HdmiDeviceInfo.DEVICE_RECORDER:
                            return TYPE_CEC_DEVICE_RECORDER;
                        case HdmiDeviceInfo.DEVICE_PLAYBACK:
                            return TYPE_CEC_DEVICE_PLAYBACK;
                        default:
                            return TYPE_CEC_DEVICE;
                    }
                } else if (info.getHdmiDeviceInfo().isMhlDevice()) {
                    return TYPE_MHL_MOBILE;
                }
            }
            return info.getType();
        }
    }
}
