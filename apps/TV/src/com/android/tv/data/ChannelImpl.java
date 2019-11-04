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

package com.android.tv.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import com.android.tv.common.CommonConstants;
import com.android.tv.common.util.CommonUtils;
import com.android.tv.data.api.Channel;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;
import com.android.tv.util.images.ImageLoader;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A convenience class to create and insert channel entries into the database. */
public final class ChannelImpl implements Channel {
    private static final String TAG = "ChannelImpl";

    /** Compares the channel numbers of channels which belong to the same input. */
    public static final Comparator<Channel> CHANNEL_NUMBER_COMPARATOR =
            (Channel lhs, Channel rhs) ->
                    ChannelNumber.compare(lhs.getDisplayNumber(), rhs.getDisplayNumber());

    private static final int APP_LINK_TYPE_NOT_SET = 0;
    private static final String INVALID_PACKAGE_NAME = "packageName";

    public static final String[] PROJECTION = {
        // Columns must match what is read in ChannelImpl.fromCursor()
        TvContract.Channels._ID,
        TvContract.Channels.COLUMN_PACKAGE_NAME,
        TvContract.Channels.COLUMN_INPUT_ID,
        TvContract.Channels.COLUMN_TYPE,
        TvContract.Channels.COLUMN_DISPLAY_NUMBER,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
        TvContract.Channels.COLUMN_DESCRIPTION,
        TvContract.Channels.COLUMN_VIDEO_FORMAT,
        TvContract.Channels.COLUMN_BROWSABLE,
        TvContract.Channels.COLUMN_SEARCHABLE,
        TvContract.Channels.COLUMN_LOCKED,
        TvContract.Channels.COLUMN_APP_LINK_TEXT,
        TvContract.Channels.COLUMN_APP_LINK_COLOR,
        TvContract.Channels.COLUMN_APP_LINK_ICON_URI,
        TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI,
        TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
        TvContract.Channels.COLUMN_NETWORK_AFFILIATION,
        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG2, // Only used in bundled input
    };

    /**
     * Creates {@code ChannelImpl} object from cursor.
     *
     * <p>The query that created the cursor MUST use {@link #PROJECTION}
     */
    public static ChannelImpl fromCursor(Cursor cursor) {
        // Columns read must match the order of {@link #PROJECTION}
        ChannelImpl channel = new ChannelImpl();
        int index = 0;
        channel.mId = cursor.getLong(index++);
        channel.mPackageName = Utils.intern(cursor.getString(index++));
        channel.mInputId = Utils.intern(cursor.getString(index++));
        channel.mType = Utils.intern(cursor.getString(index++));
        channel.mDisplayNumber = normalizeDisplayNumber(cursor.getString(index++));
        channel.mDisplayName = cursor.getString(index++);
        channel.mDescription = cursor.getString(index++);
        channel.mVideoFormat = Utils.intern(cursor.getString(index++));
        channel.mBrowsable = cursor.getInt(index++) == 1;
        channel.mSearchable = cursor.getInt(index++) == 1;
        channel.mLocked = cursor.getInt(index++) == 1;
        channel.mAppLinkText = cursor.getString(index++);
        channel.mAppLinkColor = cursor.getInt(index++);
        channel.mAppLinkIconUri = cursor.getString(index++);
        channel.mAppLinkPosterArtUri = cursor.getString(index++);
        channel.mAppLinkIntentUri = cursor.getString(index++);
        channel.mNetworkAffiliation = cursor.getString(index++);
        if (CommonUtils.isBundledInput(channel.mInputId)) {
            channel.mRecordingProhibited = cursor.getInt(index++) != 0;
        }
        return channel;
    }

    /** Replaces the channel number separator with dash('-'). */
    public static String normalizeDisplayNumber(String string) {
        if (!TextUtils.isEmpty(string)) {
            int length = string.length();
            for (int i = 0; i < length; i++) {
                char c = string.charAt(i);
                if (c == '.'
                        || Character.isWhitespace(c)
                        || Character.getType(c) == Character.DASH_PUNCTUATION) {
                    StringBuilder sb = new StringBuilder(string);
                    sb.setCharAt(i, CHANNEL_NUMBER_DELIMITER);
                    return sb.toString();
                }
            }
        }
        return string;
    }

    /** ID of this channel. Matches to BaseColumns._ID. */
    private long mId;

    private String mPackageName;
    private String mInputId;
    private String mType;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private String mVideoFormat;
    private boolean mBrowsable;
    private boolean mSearchable;
    private boolean mLocked;
    private boolean mIsPassthrough;
    private String mAppLinkText;
    private int mAppLinkColor;
    private String mAppLinkIconUri;
    private String mAppLinkPosterArtUri;
    private String mAppLinkIntentUri;
    private Intent mAppLinkIntent;
    private String mNetworkAffiliation;
    private int mAppLinkType;
    private String mLogoUri;
    private boolean mRecordingProhibited;

    private boolean mChannelLogoExist;

    private ChannelImpl() {
        // Do nothing.
    }

    @Override
    public long getId() {
        return mId;
    }

    @Override
    public Uri getUri() {
        if (isPassthrough()) {
            return TvContract.buildChannelUriForPassthroughInput(mInputId);
        } else {
            return TvContract.buildChannelUri(mId);
        }
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public String getInputId() {
        return mInputId;
    }

    @Override
    public String getType() {
        return mType;
    }

    @Override
    public String getDisplayNumber() {
        return mDisplayNumber;
    }

    @Override
    @Nullable
    public String getDisplayName() {
        return mDisplayName;
    }

    @Override
    public String getDescription() {
        return mDescription;
    }

    @Override
    public String getVideoFormat() {
        return mVideoFormat;
    }

    @Override
    public boolean isPassthrough() {
        return mIsPassthrough;
    }

    /**
     * Gets identification text for displaying or debugging. It's made from Channels' display number
     * plus their display name.
     */
    @Override
    public String getDisplayText() {
        return TextUtils.isEmpty(mDisplayName)
                ? mDisplayNumber
                : mDisplayNumber + " " + mDisplayName;
    }

    @Override
    public String getAppLinkText() {
        return mAppLinkText;
    }

    @Override
    public int getAppLinkColor() {
        return mAppLinkColor;
    }

    @Override
    public String getAppLinkIconUri() {
        return mAppLinkIconUri;
    }

    @Override
    public String getAppLinkPosterArtUri() {
        return mAppLinkPosterArtUri;
    }

    @Override
    public String getAppLinkIntentUri() {
        return mAppLinkIntentUri;
    }

    @Override
    public String getNetworkAffiliation() {
        return mNetworkAffiliation;
    }

    /** Returns channel logo uri which is got from cloud, it's used only for ChannelLogoFetcher. */
    @Override
    public String getLogoUri() {
        return mLogoUri;
    }

    @Override
    public boolean isRecordingProhibited() {
        return mRecordingProhibited;
    }

    /** Checks whether this channel is physical tuner channel or not. */
    @Override
    public boolean isPhysicalTunerChannel() {
        return !TextUtils.isEmpty(mType) && !TvContract.Channels.TYPE_OTHER.equals(mType);
    }

    /** Checks if two channels equal by checking ids. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChannelImpl)) {
            return false;
        }
        ChannelImpl other = (ChannelImpl) o;
        // All pass-through TV channels have INVALID_ID value for mId.
        return mId == other.mId
                && TextUtils.equals(mInputId, other.mInputId)
                && mIsPassthrough == other.mIsPassthrough;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mInputId, mIsPassthrough);
    }

    @Override
    public boolean isBrowsable() {
        return mBrowsable;
    }

    /** Checks whether this channel is searchable or not. */
    @Override
    public boolean isSearchable() {
        return mSearchable;
    }

    @Override
    public boolean isLocked() {
        return mLocked;
    }

    public void setBrowsable(boolean browsable) {
        mBrowsable = browsable;
    }

    public void setLocked(boolean locked) {
        mLocked = locked;
    }

    /** Sets channel logo uri which is got from cloud. */
    public void setLogoUri(String logoUri) {
        mLogoUri = logoUri;
    }

    @Override
    public void setNetworkAffiliation(String networkAffiliation) {
        mNetworkAffiliation = networkAffiliation;
    }

    /**
     * Check whether {@code other} has same read-only channel info as this. But, it cannot check two
     * channels have same logos. It also excludes browsable and locked, because two fields are
     * changed by TV app.
     */
    @Override
    public boolean hasSameReadOnlyInfo(Channel other) {
        return other != null
                && Objects.equals(mId, other.getId())
                && Objects.equals(mPackageName, other.getPackageName())
                && Objects.equals(mInputId, other.getInputId())
                && Objects.equals(mType, other.getType())
                && Objects.equals(mDisplayNumber, other.getDisplayNumber())
                && Objects.equals(mDisplayName, other.getDisplayName())
                && Objects.equals(mDescription, other.getDescription())
                && Objects.equals(mVideoFormat, other.getVideoFormat())
                && mIsPassthrough == other.isPassthrough()
                && Objects.equals(mAppLinkText, other.getAppLinkText())
                && mAppLinkColor == other.getAppLinkColor()
                && Objects.equals(mAppLinkIconUri, other.getAppLinkIconUri())
                && Objects.equals(mAppLinkPosterArtUri, other.getAppLinkPosterArtUri())
                && Objects.equals(mAppLinkIntentUri, other.getAppLinkIntentUri())
                && Objects.equals(mRecordingProhibited, other.isRecordingProhibited());
    }

    @Override
    public String toString() {
        return "Channel{"
                + "id="
                + mId
                + ", packageName="
                + mPackageName
                + ", inputId="
                + mInputId
                + ", type="
                + mType
                + ", displayNumber="
                + mDisplayNumber
                + ", displayName="
                + mDisplayName
                + ", description="
                + mDescription
                + ", videoFormat="
                + mVideoFormat
                + ", isPassthrough="
                + mIsPassthrough
                + ", browsable="
                + mBrowsable
                + ", searchable="
                + mSearchable
                + ", locked="
                + mLocked
                + ", appLinkText="
                + mAppLinkText
                + ", recordingProhibited="
                + mRecordingProhibited
                + "}";
    }

    @Override
    public void copyFrom(Channel channel) {
        if (channel instanceof ChannelImpl) {
            copyFrom((ChannelImpl) channel);
        } else {
            // copy what we can
            mId = channel.getId();
            mPackageName = channel.getPackageName();
            mInputId = channel.getInputId();
            mType = channel.getType();
            mDisplayNumber = channel.getDisplayNumber();
            mDisplayName = channel.getDisplayName();
            mDescription = channel.getDescription();
            mVideoFormat = channel.getVideoFormat();
            mIsPassthrough = channel.isPassthrough();
            mBrowsable = channel.isBrowsable();
            mSearchable = channel.isSearchable();
            mLocked = channel.isLocked();
            mAppLinkText = channel.getAppLinkText();
            mAppLinkColor = channel.getAppLinkColor();
            mAppLinkIconUri = channel.getAppLinkIconUri();
            mAppLinkPosterArtUri = channel.getAppLinkPosterArtUri();
            mAppLinkIntentUri = channel.getAppLinkIntentUri();
            mNetworkAffiliation = channel.getNetworkAffiliation();
            mRecordingProhibited = channel.isRecordingProhibited();
            mChannelLogoExist = channel.channelLogoExists();
            mNetworkAffiliation = channel.getNetworkAffiliation();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void copyFrom(ChannelImpl channel) {
        ChannelImpl other = (ChannelImpl) channel;
        if (this == other) {
            return;
        }
        mId = other.mId;
        mPackageName = other.mPackageName;
        mInputId = other.mInputId;
        mType = other.mType;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mVideoFormat = other.mVideoFormat;
        mIsPassthrough = other.mIsPassthrough;
        mBrowsable = other.mBrowsable;
        mSearchable = other.mSearchable;
        mLocked = other.mLocked;
        mAppLinkText = other.mAppLinkText;
        mAppLinkColor = other.mAppLinkColor;
        mAppLinkIconUri = other.mAppLinkIconUri;
        mAppLinkPosterArtUri = other.mAppLinkPosterArtUri;
        mAppLinkIntentUri = other.mAppLinkIntentUri;
        mNetworkAffiliation = channel.mNetworkAffiliation;
        mAppLinkIntent = other.mAppLinkIntent;
        mAppLinkType = other.mAppLinkType;
        mRecordingProhibited = other.mRecordingProhibited;
        mChannelLogoExist = other.mChannelLogoExist;
    }

    /** Creates a channel for a passthrough TV input. */
    public static ChannelImpl createPassthroughChannel(Uri uri) {
        if (!TvContract.isChannelUriForPassthroughInput(uri)) {
            throw new IllegalArgumentException("URI is not a passthrough channel URI");
        }
        String inputId = uri.getPathSegments().get(1);
        return createPassthroughChannel(inputId);
    }

    /** Creates a channel for a passthrough TV input with {@code inputId}. */
    public static ChannelImpl createPassthroughChannel(String inputId) {
        return new Builder().setInputId(inputId).setPassthrough(true).build();
    }

    /** Checks whether the channel is valid or not. */
    public static boolean isValid(Channel channel) {
        return channel != null && (channel.getId() != INVALID_ID || channel.isPassthrough());
    }

    /**
     * Builder class for {@code ChannelImpl}. Suppress using this outside of ChannelDataManager so
     * Channels could be managed by ChannelDataManager.
     */
    public static final class Builder {
        private final ChannelImpl mChannel;

        public Builder() {
            mChannel = new ChannelImpl();
            // Fill initial data.
            mChannel.mId = INVALID_ID;
            mChannel.mPackageName = INVALID_PACKAGE_NAME;
            mChannel.mInputId = "inputId";
            mChannel.mType = "type";
            mChannel.mDisplayNumber = "0";
            mChannel.mDisplayName = "name";
            mChannel.mDescription = "description";
            mChannel.mBrowsable = true;
            mChannel.mSearchable = true;
        }

        public Builder(Channel other) {
            mChannel = new ChannelImpl();
            mChannel.copyFrom(other);
        }

        @VisibleForTesting
        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        @VisibleForTesting
        public Builder setPackageName(String packageName) {
            mChannel.mPackageName = packageName;
            return this;
        }

        public Builder setInputId(String inputId) {
            mChannel.mInputId = inputId;
            return this;
        }

        public Builder setType(String type) {
            mChannel.mType = type;
            return this;
        }

        @VisibleForTesting
        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = normalizeDisplayNumber(displayNumber);
            return this;
        }

        @VisibleForTesting
        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        @VisibleForTesting
        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setVideoFormat(String videoFormat) {
            mChannel.mVideoFormat = videoFormat;
            return this;
        }

        public Builder setBrowsable(boolean browsable) {
            mChannel.mBrowsable = browsable;
            return this;
        }

        public Builder setSearchable(boolean searchable) {
            mChannel.mSearchable = searchable;
            return this;
        }

        public Builder setLocked(boolean locked) {
            mChannel.mLocked = locked;
            return this;
        }

        public Builder setPassthrough(boolean isPassthrough) {
            mChannel.mIsPassthrough = isPassthrough;
            return this;
        }

        @VisibleForTesting
        public Builder setAppLinkText(String appLinkText) {
            mChannel.mAppLinkText = appLinkText;
            return this;
        }

        @VisibleForTesting
        public Builder setNetworkAffiliation(String networkAffiliation) {
            mChannel.mNetworkAffiliation = networkAffiliation;
            return this;
        }

        public Builder setAppLinkColor(int appLinkColor) {
            mChannel.mAppLinkColor = appLinkColor;
            return this;
        }

        public Builder setAppLinkIconUri(String appLinkIconUri) {
            mChannel.mAppLinkIconUri = appLinkIconUri;
            return this;
        }

        public Builder setAppLinkPosterArtUri(String appLinkPosterArtUri) {
            mChannel.mAppLinkPosterArtUri = appLinkPosterArtUri;
            return this;
        }

        @VisibleForTesting
        public Builder setAppLinkIntentUri(String appLinkIntentUri) {
            mChannel.mAppLinkIntentUri = appLinkIntentUri;
            return this;
        }

        public Builder setRecordingProhibited(boolean recordingProhibited) {
            mChannel.mRecordingProhibited = recordingProhibited;
            return this;
        }

        public ChannelImpl build() {
            ChannelImpl channel = new ChannelImpl();
            channel.copyFrom(mChannel);
            return channel;
        }
    }

    /** Prefetches the images for this channel. */
    public void prefetchImage(Context context, int type, int maxWidth, int maxHeight) {
        String uriString = getImageUriString(type);
        if (!TextUtils.isEmpty(uriString)) {
            ImageLoader.prefetchBitmap(context, uriString, maxWidth, maxHeight);
        }
    }

    /**
     * Loads the bitmap of this channel and returns it via {@code callback}. The loaded bitmap will
     * be cached and resized with given params.
     *
     * <p>Note that it may directly call {@code callback} if the bitmap is already loaded.
     *
     * @param context A context.
     * @param type The type of bitmap which will be loaded. It should be one of follows: {@link
     *     Channel#LOAD_IMAGE_TYPE_CHANNEL_LOGO}, {@link Channel#LOAD_IMAGE_TYPE_APP_LINK_ICON}, or
     *     {@link Channel#LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART}.
     * @param maxWidth The max width of the loaded bitmap.
     * @param maxHeight The max height of the loaded bitmap.
     * @param callback A callback which will be called after the loading finished.
     */
    @UiThread
    public void loadBitmap(
            Context context,
            final int type,
            int maxWidth,
            int maxHeight,
            ImageLoader.ImageLoaderCallback callback) {
        String uriString = getImageUriString(type);
        ImageLoader.loadBitmap(context, uriString, maxWidth, maxHeight, callback);
    }

    /**
     * Sets if the channel logo exists. This method should be only called from {@link
     * ChannelDataManager}.
     */
    @Override
    public void setChannelLogoExist(boolean exist) {
        mChannelLogoExist = exist;
    }

    /** Returns if channel logo exists. */
    public boolean channelLogoExists() {
        return mChannelLogoExist;
    }

    /**
     * Returns the type of app link for this channel. It returns {@link
     * Channel#APP_LINK_TYPE_CHANNEL} if the channel has a non null app link text and a valid app
     * link intent, it returns {@link Channel#APP_LINK_TYPE_APP} if the input service which holds
     * the channel has leanback launch intent, and it returns {@link Channel#APP_LINK_TYPE_NONE}
     * otherwise.
     */
    public int getAppLinkType(Context context) {
        if (mAppLinkType == APP_LINK_TYPE_NOT_SET) {
            initAppLinkTypeAndIntent(context);
        }
        return mAppLinkType;
    }

    /**
     * Returns the app link intent for this channel. If the type of app link is {@link
     * Channel#APP_LINK_TYPE_NONE}, it returns {@code null}.
     */
    public Intent getAppLinkIntent(Context context) {
        if (mAppLinkType == APP_LINK_TYPE_NOT_SET) {
            initAppLinkTypeAndIntent(context);
        }
        return mAppLinkIntent;
    }

    private void initAppLinkTypeAndIntent(Context context) {
        mAppLinkType = APP_LINK_TYPE_NONE;
        mAppLinkIntent = null;
        PackageManager pm = context.getPackageManager();
        if (!TextUtils.isEmpty(mAppLinkText) && !TextUtils.isEmpty(mAppLinkIntentUri)) {
            try {
                Intent intent = Intent.parseUri(mAppLinkIntentUri, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivityInfo(pm, 0) != null) {
                    mAppLinkIntent = intent;
                    mAppLinkIntent.putExtra(
                            CommonConstants.EXTRA_APP_LINK_CHANNEL_URI, getUri().toString());
                    mAppLinkType = APP_LINK_TYPE_CHANNEL;
                    return;
                } else {
                    Log.w(TAG, "No activity exists to handle : " + mAppLinkIntentUri);
                }
            } catch (URISyntaxException e) {
                Log.w(TAG, "Unable to set app link for " + mAppLinkIntentUri, e);
                // Do nothing.
            }
        }
        if (mPackageName.equals(context.getApplicationContext().getPackageName())) {
            return;
        }
        mAppLinkIntent = pm.getLeanbackLaunchIntentForPackage(mPackageName);
        if (mAppLinkIntent != null) {
            mAppLinkIntent.putExtra(
                    CommonConstants.EXTRA_APP_LINK_CHANNEL_URI, getUri().toString());
            mAppLinkType = APP_LINK_TYPE_APP;
        }
    }

    private String getImageUriString(int type) {
        switch (type) {
            case LOAD_IMAGE_TYPE_CHANNEL_LOGO:
                return TvContract.buildChannelLogoUri(mId).toString();
            case LOAD_IMAGE_TYPE_APP_LINK_ICON:
                return mAppLinkIconUri;
            case LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART:
                return mAppLinkPosterArtUri;
        }
        return null;
    }

    /**
     * Default Channel ordering.
     *
     * <p>Ordering
     * <li>{@link TvInputManagerHelper#isPartnerInput(String)}
     * <li>{@link #getInputLabelForChannel(Channel)}
     * <li>{@link #getInputId()}
     * <li>{@link ChannelNumber#compare(String, String)}
     * <li>
     * </ol>
     */
    public static class DefaultComparator implements Comparator<Channel> {
        private final Context mContext;
        private final TvInputManagerHelper mInputManager;
        private final Map<String, String> mInputIdToLabelMap = new HashMap<>();
        private boolean mDetectDuplicatesEnabled;

        public DefaultComparator(Context context, TvInputManagerHelper inputManager) {
            mContext = context;
            mInputManager = inputManager;
        }

        public void setDetectDuplicatesEnabled(boolean detectDuplicatesEnabled) {
            mDetectDuplicatesEnabled = detectDuplicatesEnabled;
        }

        @SuppressWarnings("ReferenceEquality")
        @Override
        public int compare(Channel lhs, Channel rhs) {
            if (lhs == rhs) {
                return 0;
            }
            // Put channels from OEM/SOC inputs first.
            boolean lhsIsPartner = mInputManager.isPartnerInput(lhs.getInputId());
            boolean rhsIsPartner = mInputManager.isPartnerInput(rhs.getInputId());
            if (lhsIsPartner != rhsIsPartner) {
                return lhsIsPartner ? -1 : 1;
            }
            // Compare the input labels.
            String lhsLabel = getInputLabelForChannel(lhs);
            String rhsLabel = getInputLabelForChannel(rhs);
            int result =
                    lhsLabel == null
                            ? (rhsLabel == null ? 0 : 1)
                            : rhsLabel == null ? -1 : lhsLabel.compareTo(rhsLabel);
            if (result != 0) {
                return result;
            }
            // Compare the input IDs. The input IDs cannot be null.
            result = lhs.getInputId().compareTo(rhs.getInputId());
            if (result != 0) {
                return result;
            }
            // Compare the channel numbers if both channels belong to the same input.
            result = ChannelNumber.compare(lhs.getDisplayNumber(), rhs.getDisplayNumber());
            if (mDetectDuplicatesEnabled && result == 0) {
                Log.w(
                        TAG,
                        "Duplicate channels detected! - \""
                                + lhs.getDisplayText()
                                + "\" and \""
                                + rhs.getDisplayText()
                                + "\"");
            }
            return result;
        }

        @VisibleForTesting
        String getInputLabelForChannel(Channel channel) {
            String label = mInputIdToLabelMap.get(channel.getInputId());
            if (label == null) {
                TvInputInfo info = mInputManager.getTvInputInfo(channel.getInputId());
                if (info != null) {
                    label = Utils.loadLabel(mContext, info);
                    if (label != null) {
                        mInputIdToLabelMap.put(channel.getInputId(), label);
                    }
                }
            }
            return label;
        }
    }
}
