/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.users;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.internal.util.UserIcons;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a GridLayout with icons for the users in the system to allow switching between users.
 * One of the uses of this is for the lock screen in auto.
 */
public class UserGridRecyclerView extends RecyclerView implements
        CarUserManagerHelper.OnUsersUpdateListener {

    private UserAdapter mAdapter;
    private CarUserManagerHelper mCarUserManagerHelper;
    private Context mContext;
    private BaseFragment mBaseFragment;
    public AddNewUserTask mAddNewUserTask;
    public boolean mEnableAddUserButton;

    public UserGridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        mEnableAddUserButton = true;

        addItemDecoration(new ItemSpacingDecoration(context.getResources().getDimensionPixelSize(
                R.dimen.user_switcher_vertical_spacing_between_users)));
    }

    /**
     * Register listener for any update to the users
     */
    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mCarUserManagerHelper.registerOnUsersUpdateListener(this);
    }

    /**
     * Unregisters listener checking for any change to the users
     */
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(this);
        if (mAddNewUserTask != null) {
            mAddNewUserTask.cancel(/* mayInterruptIfRunning= */ false);
        }
    }

    /**
     * Initializes the adapter that populates the grid layout
     */
    public void buildAdapter() {
        List<UserRecord> userRecords = createUserRecords(mCarUserManagerHelper
                .getAllUsers());
        mAdapter = new UserAdapter(mContext, userRecords);
        super.setAdapter(mAdapter);
    }

    private List<UserRecord> createUserRecords(List<UserInfo> userInfoList) {
        List<UserRecord> userRecords = new ArrayList<>();

        // If the foreground user CANNOT switch to other users, only display the foreground user.
        if (!mCarUserManagerHelper.canForegroundUserSwitchUsers()) {
            userRecords.add(createForegroundUserRecord());
            return userRecords;
        }

        // If the foreground user CAN switch to other users, iterate through all users.
        for (UserInfo userInfo : userInfoList) {
            boolean isForeground =
                    mCarUserManagerHelper.getCurrentForegroundUserId() == userInfo.id;

            if (!isForeground && userInfo.isGuest()) {
                // Don't display temporary running background guests in the switcher.
                continue;
            }

            UserRecord record = new UserRecord(userInfo, false /* isStartGuestSession */,
                    false /* isAddUser */, isForeground);
            userRecords.add(record);
        }

        // Add start guest user record if the system is not logged in as guest already.
        if (!mCarUserManagerHelper.isForegroundUserGuest()) {
            userRecords.add(createStartGuestUserRecord());
        }

        // Add "add user" record if the foreground user can add users
        if (mCarUserManagerHelper.canForegroundUserAddUsers()) {
            userRecords.add(createAddUserRecord());
        }

        return userRecords;
    }

    private UserRecord createForegroundUserRecord() {
        return new UserRecord(mCarUserManagerHelper.getCurrentForegroundUserInfo(),
                false /* isStartGuestSession */, false /* isAddUser */, true /* isForeground */);
    }

    /**
     * Show the "Add User" Button
     */
    public void enableAddUser() {
        mEnableAddUserButton = true;
        onUsersUpdate();
    }

    /**
     * Hide the "Add User" Button
     */
    public void disableAddUser() {
        mEnableAddUserButton = false;
        onUsersUpdate();
    }

    /**
     * Create guest user record
     */
    private UserRecord createStartGuestUserRecord() {
        UserInfo userInfo = new UserInfo();
        userInfo.name = mContext.getString(R.string.start_guest_session);
        return new UserRecord(userInfo, true /* isStartGuestSession */,
                false /* isAddUser */, false /* isForeground */);
    }

    /**
     * Create add user record
     */
    private UserRecord createAddUserRecord() {
        UserInfo userInfo = new UserInfo();
        userInfo.name = mContext.getString(R.string.user_add_user_menu);
        return new UserRecord(userInfo, false /* isStartGuestSession */,
                true /* isAddUser */, false /* isForeground */);
    }

    public void setFragment(BaseFragment fragment) {
        mBaseFragment = fragment;
    }

    @Override
    public void onUsersUpdate() {
        // If you can show the add user button, there is no restriction
        mAdapter.setAddUserRestricted(!mEnableAddUserButton);
        mAdapter.clearUsers();
        mAdapter.updateUsers(createUserRecords(mCarUserManagerHelper
                .getAllUsers()));
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Adapter to populate the grid layout with the available user profiles
     */
    public final class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserAdapterViewHolder>
            implements AddNewUserTask.AddNewUserListener {

        private final Context mContext;
        private final Resources mRes;
        private final String mGuestName;

        private List<UserRecord> mUsers;
        private String mNewUserName;
        // View that holds the add user button.  Used to enable/disable the view
        private View mAddUserView;
        private float mOpacityDisabled;
        private float mOpacityEnabled;
        private boolean mIsAddUserRestricted;

        private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
            mAddNewUserTask = new AddNewUserTask(mCarUserManagerHelper, /* addNewUserListener= */
                    this);
            mAddNewUserTask.execute(mNewUserName);
        };

        /**
         * Enable the "add user" button if the user cancels adding an user
         */
        private final ConfirmationDialogFragment.RejectListener mRejectListener =
                arguments -> enableAddView();


        public UserAdapter(Context context, List<UserRecord> users) {
            mRes = context.getResources();
            mContext = context;
            updateUsers(users);
            mGuestName = mRes.getString(R.string.user_guest);
            mNewUserName = mRes.getString(R.string.user_new_user_name);
            mOpacityDisabled = mRes.getFloat(R.dimen.opacity_disabled);
            mOpacityEnabled = mRes.getFloat(R.dimen.opacity_enabled);
        }

        /**
         * Removes all the users from the User Grid.
         */
        public void clearUsers() {
            mUsers.clear();
        }

        /**
         * Refreshes the User Grid with the new List of users.
         */
        public void updateUsers(List<UserRecord> users) {
            mUsers = users;
        }

        @Override
        public UserAdapterViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext)
                    .inflate(R.layout.user_switcher_pod, parent, false);
            view.setAlpha(mOpacityEnabled);
            view.bringToFront();
            return new UserAdapterViewHolder(view);
        }

        @Override
        public void onBindViewHolder(UserAdapterViewHolder holder, int position) {
            UserRecord userRecord = mUsers.get(position);
            RoundedBitmapDrawable circleIcon = RoundedBitmapDrawableFactory.create(mRes,
                    getUserRecordIcon(userRecord));
            circleIcon.setCircular(true);
            holder.mUserAvatarImageView.setImageDrawable(circleIcon);
            holder.mUserNameTextView.setText(userRecord.mInfo.name);

            // Defaults to 100% opacity and no circle around the icon.
            holder.mView.setAlpha(mOpacityEnabled);
            holder.mFrame.setBackgroundResource(0);

            // Foreground user record.
            if (userRecord.mIsForeground) {
                // Add a circle around the icon.
                holder.mFrame.setBackgroundResource(R.drawable.user_avatar_bg_circle);
                // Go back to quick settings if user selected is already the foreground user.
                holder.mView.setOnClickListener(v -> mBaseFragment.getActivity().onBackPressed());
                return;
            }

            // Start guest session record.
            if (userRecord.mIsStartGuestSession) {
                holder.mView.setOnClickListener(v -> handleGuestSessionClicked());
                return;
            }

            // Add user record.
            if (userRecord.mIsAddUser) {
                if (mIsAddUserRestricted) {
                    // If there are restrictions, show a 50% opaque "add user" view
                    holder.mView.setAlpha(mOpacityDisabled);
                    holder.mView.setOnClickListener(
                            v -> mBaseFragment.getFragmentController().showBlockingMessage());
                } else {
                    holder.mView.setOnClickListener(v -> handleAddUserClicked(v));
                }
                return;
            }

            // User record;
            holder.mView.setOnClickListener(v -> handleUserSwitch(userRecord.mInfo));
        }

        /**
         * Specify if adding a user should be restricted.
         *
         * @param isAddUserRestricted should adding a user be restricted
         */
        public void setAddUserRestricted(boolean isAddUserRestricted) {
            mIsAddUserRestricted = isAddUserRestricted;
        }

        private void handleUserSwitch(UserInfo userInfo) {
            if (mCarUserManagerHelper.switchToUser(userInfo)) {
                // Successful switch, close Settings app.
                mBaseFragment.getActivity().finish();
            }
        }

        private void handleGuestSessionClicked() {
            if (mCarUserManagerHelper.startGuestSession(mGuestName)) {
                // Successful start, will switch to guest now. Close Settings app.
                mBaseFragment.getActivity().finish();
            }
        }

        private void handleAddUserClicked(View addUserView) {
            if (mCarUserManagerHelper.isUserLimitReached()) {
                showMaxUsersLimitReachedDialog();
            } else {
                mAddUserView = addUserView;
                // Disable button so it cannot be clicked multiple times
                mAddUserView.setEnabled(false);
                showConfirmCreateNewUserDialog();
            }
        }

        private void showMaxUsersLimitReachedDialog() {
            MaxUsersLimitReachedDialog dialog = new MaxUsersLimitReachedDialog(
                    mCarUserManagerHelper.getMaxSupportedRealUsers());
            if (mBaseFragment != null) {
                dialog.show(mBaseFragment);
            }
        }

        private void showConfirmCreateNewUserDialog() {
            ConfirmationDialogFragment dialogFragment =
                    UsersDialogProvider.getConfirmCreateNewUserDialogFragment(getContext(),
                            mConfirmListener, mRejectListener);
            dialogFragment.show(mBaseFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);
        }

        private Bitmap getUserRecordIcon(UserRecord userRecord) {
            if (userRecord.mIsStartGuestSession) {
                return mCarUserManagerHelper.getGuestDefaultIcon();
            }

            if (userRecord.mIsAddUser) {
                return UserIcons.convertToBitmap(mContext.getDrawable(R.drawable.user_add_circle));
            }

            return mCarUserManagerHelper.getUserIcon(userRecord.mInfo);
        }


        @Override
        public void onUserAddedSuccess() {
            enableAddView();
            // New user added. Will switch to new user, therefore close the app.
            mBaseFragment.getActivity().finish();
        }

        @Override
        public void onUserAddedFailure() {
            enableAddView();
            // Display failure dialog.
            if (mBaseFragment != null) {
                ErrorDialog.show(mBaseFragment, R.string.add_user_error_title);
            }
        }

        @Override
        public int getItemCount() {
            return mUsers.size();
        }

        /**
         * Layout for each individual pod in the Grid RecyclerView
         */
        public class UserAdapterViewHolder extends RecyclerView.ViewHolder {

            public ImageView mUserAvatarImageView;
            public TextView mUserNameTextView;
            public View mView;
            public FrameLayout mFrame;

            public UserAdapterViewHolder(View view) {
                super(view);
                mView = view;
                mUserAvatarImageView = view.findViewById(R.id.user_avatar);
                mUserNameTextView = view.findViewById(R.id.user_name);
                mFrame = view.findViewById(R.id.current_user_frame);
            }
        }

        private void enableAddView() {
            if (mAddUserView != null) {
                mAddUserView.setEnabled(true);
            }
        }
    }

    /**
     * Object wrapper class for the userInfo.  Use it to distinguish if a profile is a
     * guest profile, add user profile, or the foreground user.
     */
    public static final class UserRecord {

        public final UserInfo mInfo;
        public final boolean mIsStartGuestSession;
        public final boolean mIsAddUser;
        public final boolean mIsForeground;

        public UserRecord(UserInfo userInfo, boolean isStartGuestSession, boolean isAddUser,
                boolean isForeground) {
            mInfo = userInfo;
            mIsStartGuestSession = isStartGuestSession;
            mIsAddUser = isAddUser;
            mIsForeground = isForeground;
        }
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private int mItemSpacing;

        private ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item except for GridLayoutManager.
            if (position == state.getItemCount() - 1
                    && !(parent.getLayoutManager() instanceof GridLayoutManager)) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }
}
