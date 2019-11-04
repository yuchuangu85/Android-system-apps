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
package com.android.car.notification;

import static com.android.car.assist.client.CarAssistUtils.isCarCompatibleMessagingNotification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.VisibleForTesting;

import com.android.car.notification.template.BasicNotificationViewHolder;
import com.android.car.notification.template.CallNotificationViewHolder;
import com.android.car.notification.template.EmergencyNotificationViewHolder;
import com.android.car.notification.template.InboxNotificationViewHolder;
import com.android.car.notification.template.MessageNotificationViewHolder;
import com.android.car.notification.template.NavigationNotificationViewHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Notification Manager for heads-up notifications in car.
 */
public class CarHeadsUpNotificationManager
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
    private static final String TAG = CarHeadsUpNotificationManager.class.getSimpleName();

    private final Beeper mBeeper;
    private final Context mContext;
    private final boolean mEnableNavigationHeadsup;
    private final long mDuration;
    private final long mMinDisplayDuration;
    private final long mEnterAnimationDuration;
    private final long mAlphaEnterAnimationDuration;
    private final long mExitAnimationDuration;
    private final int mNotificationHeadsUpCardMarginTop;

    private final KeyguardManager mKeyguardManager;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final PreprocessingManager mPreprocessingManager;
    private final WindowManager mWindowManager;
    private final LayoutInflater mInflater;

    private boolean mShouldRestrictMessagePreview;
    private NotificationClickHandlerFactory mClickHandlerFactory;
    private NotificationDataManager mNotificationDataManager;

    // key for the map is the statusbarnotification key
    private final Map<String, HeadsUpEntry> mActiveHeadsUpNotifications;
    // view that contains scrim and notification content
    protected final View mHeadsUpPanel;
    // framelayout that notification content should be added to.
    protected final FrameLayout mHeadsUpContentFrame;

    public CarHeadsUpNotificationManager(Context context,
            NotificationClickHandlerFactory clickHandlerFactory,
            NotificationDataManager notificationDataManager) {
        mContext = context.getApplicationContext();
        mEnableNavigationHeadsup =
                context.getResources().getBoolean(R.bool.config_showNavigationHeadsup);
        mClickHandlerFactory = clickHandlerFactory;
        mNotificationDataManager = notificationDataManager;
        mBeeper = new Beeper(mContext);
        mDuration = mContext.getResources().getInteger(R.integer.headsup_notification_duration_ms);
        mNotificationHeadsUpCardMarginTop = (int) mContext.getResources().getDimension(
                R.dimen.headsup_notification_top_margin);
        mMinDisplayDuration = mContext.getResources().getInteger(
                R.integer.heads_up_notification_minimum_time);
        mEnterAnimationDuration =
                mContext.getResources().getInteger(R.integer.headsup_total_enter_duration_ms);
        mAlphaEnterAnimationDuration =
                mContext.getResources().getInteger(R.integer.headsup_alpha_enter_duration_ms);
        mExitAnimationDuration =
                mContext.getResources().getInteger(R.integer.headsup_exit_duration_ms);
        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        mPreprocessingManager = PreprocessingManager.getInstance(context);
        mWindowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = LayoutInflater.from(mContext);
        mActiveHeadsUpNotifications = new HashMap<>();
        mHeadsUpPanel = createHeadsUpPanel();
        mHeadsUpContentFrame = mHeadsUpPanel.findViewById(R.id.headsup_content);
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        addHeadsUpPanelToDisplay();
    }

    /**
     * Construct and return the heads up panel.
     *
     * @return view that contains R.id.headsup_content
     */
    protected View createHeadsUpPanel() {
        return mInflater.inflate(R.layout.headsup_container, null);
    }

    /**
     * Attach the heads up panel to the display
     */
    protected void addHeadsUpPanelToDisplay() {
        WindowManager.LayoutParams wrapperParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                // This type allows covering status bar and receiving touch input
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wrapperParams.gravity = Gravity.TOP;
        mHeadsUpPanel.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mHeadsUpPanel, wrapperParams);
    }

    /**
     * Set the Heads Up view to visible
     */
    protected void setHeadsUpVisible() {
        mHeadsUpPanel.setVisibility(View.VISIBLE);
    }

    /**
     * Show the notification as a heads-up if it meets the criteria.
     */
    public void maybeShowHeadsUp(
            StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap,
            Map<String, StatusBarNotification> activeNotifications) {
        if (!shouldShowHeadsUp(statusBarNotification, rankingMap)) {
            // check if this is a update to the existing notification and if it should still show
            // as a heads up or not.
            HeadsUpEntry currentActiveHeadsUpNotification = mActiveHeadsUpNotifications.get(
                    statusBarNotification.getKey());
            if (currentActiveHeadsUpNotification == null) {
                activeNotifications.put(statusBarNotification.getKey(), statusBarNotification);
                return;
            }
            if (CarNotificationDiff.sameNotificationKey(
                    currentActiveHeadsUpNotification.getStatusBarNotification(),
                    statusBarNotification)
                    && currentActiveHeadsUpNotification.getHandler().hasMessagesOrCallbacks()) {
                animateOutHUN(statusBarNotification);
            }
            activeNotifications.put(statusBarNotification.getKey(), statusBarNotification);
            return;
        }
        if (!activeNotifications.containsKey(statusBarNotification.getKey()) || canUpdate(
                statusBarNotification) || alertAgain(statusBarNotification.getNotification())) {
            showHeadsUp(mPreprocessingManager.optimizeForDriving(statusBarNotification),
                    rankingMap);
        }
        activeNotifications.put(statusBarNotification.getKey(), statusBarNotification);
    }

    /**
     * This method gets called when an app wants to cancel or withdraw its notification.
     */
    public void maybeRemoveHeadsUp(StatusBarNotification statusBarNotification) {
        HeadsUpEntry currentActiveHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        // if the heads up notification is already removed do nothing.
        if (currentActiveHeadsUpNotification == null) {
            return;
        }

        long totalDisplayDuration =
                System.currentTimeMillis() - currentActiveHeadsUpNotification.getPostTime();
        // ongoing notification that has passed the minimum threshold display time.
        if (totalDisplayDuration >= mMinDisplayDuration) {
            animateOutHUN(statusBarNotification);
            return;
        }

        long earliestRemovalTime = mMinDisplayDuration - totalDisplayDuration;

        currentActiveHeadsUpNotification.getHandler().postDelayed(() ->
                animateOutHUN(statusBarNotification), earliestRemovalTime);
    }

    /**
     * Returns true if the notification's flag is not set to
     * {@link Notification#FLAG_ONLY_ALERT_ONCE}
     */
    private boolean alertAgain(Notification newNotification) {
        return (newNotification.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0;
    }

    /**
     * Return true if the currently displaying notification have the same key as the new added
     * notification. In that case it will be considered as an update to the currently displayed
     * notification.
     */
    private boolean isUpdate(StatusBarNotification statusBarNotification) {
        HeadsUpEntry currentActiveHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        if (currentActiveHeadsUpNotification == null) {
            return false;
        }
        return CarNotificationDiff.sameNotificationKey(
                currentActiveHeadsUpNotification.getStatusBarNotification(),
                statusBarNotification);
    }

    /**
     * Updates only when the notification is being displayed.
     */
    private boolean canUpdate(StatusBarNotification statusBarNotification) {
        HeadsUpEntry currentActiveHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        return currentActiveHeadsUpNotification != null && System.currentTimeMillis() -
                currentActiveHeadsUpNotification.getPostTime() < mDuration;
    }

    /**
     * Returns the active headsUpEntry or creates a new one while adding it to the list of
     * mActiveHeadsUpNotifications.
     */
    private HeadsUpEntry addNewHeadsUpEntry(StatusBarNotification statusBarNotification) {
        HeadsUpEntry currentActiveHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        if (currentActiveHeadsUpNotification == null) {
            currentActiveHeadsUpNotification = new HeadsUpEntry(statusBarNotification);
            mActiveHeadsUpNotifications.put(statusBarNotification.getKey(),
                    currentActiveHeadsUpNotification);
            currentActiveHeadsUpNotification.isAlertAgain = alertAgain(
                    statusBarNotification.getNotification());
            currentActiveHeadsUpNotification.isNewHeadsUp = true;
            return currentActiveHeadsUpNotification;
        }
        currentActiveHeadsUpNotification.isNewHeadsUp = false;
        currentActiveHeadsUpNotification.isAlertAgain = alertAgain(
                statusBarNotification.getNotification());
        if (currentActiveHeadsUpNotification.isAlertAgain) {
            // This is a ongoing notification which needs to be alerted again to the user. This
            // requires for the post time to be updated.
            currentActiveHeadsUpNotification.updatePostTime();
        }
        return currentActiveHeadsUpNotification;
    }

    /**
     * Controls three major conditions while showing heads up notification.
     * <p>
     * <ol>
     * <li> When a new HUN comes in it will be displayed with animations
     * <li> If an update to existing HUN comes in which enforces to alert the HUN again to user,
     * then the post time will be updated to current time. This will only be done if {@link
     * Notification#FLAG_ONLY_ALERT_ONCE} flag is not set.
     * <li> If an update to existing HUN comes in which just updates the data and does not want to
     * alert itself again, then the animations will not be shown and the data will get updated. This
     * will only be done if {@link Notification#FLAG_ONLY_ALERT_ONCE} flag is not set.
     * </ol>
     */
    private void showHeadsUp(StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap) {
        // Show animations only when there is no active HUN and notification is new. This check
        // needs to be done here because after this the new notification will be added to the map
        // holding ongoing notifications.
        boolean shouldShowAnimation = !isUpdate(statusBarNotification);
        HeadsUpEntry currentNotification = addNewHeadsUpEntry(statusBarNotification);
        if (currentNotification.isNewHeadsUp) {
            playSound(statusBarNotification, rankingMap);
            setHeadsUpVisible();
            setAutoDismissViews(currentNotification, statusBarNotification);
        } else if (currentNotification.isAlertAgain) {
            setAutoDismissViews(currentNotification, statusBarNotification);
        }
        @NotificationViewType int viewType = getNotificationViewType(statusBarNotification);
        mClickHandlerFactory.setHeadsUpNotificationCallBack(
                () -> animateOutHUN(statusBarNotification));
        currentNotification.setClickHandlerFactory(mClickHandlerFactory);
        switch (viewType) {
            case NotificationViewType.CAR_EMERGENCY_HEADSUP: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.car_emergency_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new EmergencyNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.NAVIGATION: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.navigation_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new NavigationNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.CALL: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.call_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new CallNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.CAR_WARNING_HEADSUP: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.car_warning_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    // Using the basic view holder because they share the same view binding logic
                    // OEMs should create view holders if needed
                    currentNotification.setViewHolder(
                            new BasicNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification, /* isInGroup= */
                        false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.CAR_INFORMATION_HEADSUP: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.car_information_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    // Using the basic view holder because they share the same view binding logic
                    // OEMs should create view holders if needed
                    currentNotification.setViewHolder(
                            new BasicNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.MESSAGE_HEADSUP: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.message_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new MessageNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                if (mShouldRestrictMessagePreview) {
                    ((MessageNotificationViewHolder) currentNotification.getViewHolder())
                            .bindRestricted(statusBarNotification, /* isInGroup= */
                                    false, /* isHeadsUp= */ true);
                } else {
                    currentNotification.getViewHolder().bind(statusBarNotification, /* isInGroup= */
                            false, /* isHeadsUp= */ true);
                }
                break;
            }
            case NotificationViewType.INBOX_HEADSUP: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.inbox_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new InboxNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
            case NotificationViewType.BASIC_HEADSUP:
            default: {
                if (currentNotification.getNotificationView() == null) {
                    currentNotification.setNotificationView(mInflater.inflate(
                            R.layout.basic_headsup_notification_template,
                            null));
                    mHeadsUpContentFrame.addView(currentNotification.getNotificationView());
                    currentNotification.setViewHolder(
                            new BasicNotificationViewHolder(
                                    currentNotification.getNotificationView(),
                                    mClickHandlerFactory));
                }
                currentNotification.getViewHolder().bind(statusBarNotification,
                        /* isInGroup= */ false, /* isHeadsUp= */ true);
                break;
            }
        }

        // measure the size of the card and make that area of the screen touchable
        currentNotification.getNotificationView().getViewTreeObserver()
                .addOnComputeInternalInsetsListener(
                        info -> setInternalInsetsInfo(info,
                                currentNotification, /* panelExpanded= */false));
        // Get the height of the notification view after onLayout()
        // in order animate the notification in
        currentNotification.getNotificationView().getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int notificationHeight =
                                currentNotification.getNotificationView().getHeight();

                        if (shouldShowAnimation) {
                            currentNotification.getNotificationView().setY(0 - notificationHeight);
                            currentNotification.getNotificationView().setAlpha(0f);

                            Interpolator yPositionInterpolator = AnimationUtils.loadInterpolator(
                                    mContext,
                                    R.interpolator.heads_up_entry_direction_interpolator);
                            Interpolator alphaInterpolator = AnimationUtils.loadInterpolator(
                                    mContext,
                                    R.interpolator.heads_up_entry_alpha_interpolator);

                            ObjectAnimator moveY = ObjectAnimator.ofFloat(
                                    currentNotification.getNotificationView(), "y", 0f);
                            moveY.setDuration(mEnterAnimationDuration);
                            moveY.setInterpolator(yPositionInterpolator);

                            ObjectAnimator alpha = ObjectAnimator.ofFloat(
                                    currentNotification.getNotificationView(), "alpha", 1f);
                            alpha.setDuration(mAlphaEnterAnimationDuration);
                            alpha.setInterpolator(alphaInterpolator);

                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(moveY, alpha);
                            animatorSet.start();

                        }
                        currentNotification.getNotificationView().getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                    }
                });

        if (currentNotification.isNewHeadsUp) {
            boolean shouldDismissOnSwipe = true;
            if (shouldDismissOnSwipe(statusBarNotification)) {
                shouldDismissOnSwipe = false;
            }
            // Add swipe gesture
            View cardView = currentNotification.getNotificationView().findViewById(R.id.card_view);
            cardView.setOnTouchListener(
                    new HeadsUpNotificationOnTouchListener(cardView, shouldDismissOnSwipe,
                            () -> resetView(statusBarNotification)));
        }
    }

    protected void setInternalInsetsInfo(ViewTreeObserver.InternalInsetsInfo info,
            HeadsUpEntry currentNotification, boolean panelExpanded) {
        // If the panel is not on screen don't modify the touch region
        if (mHeadsUpPanel.getVisibility() != View.VISIBLE) return;
        int[] mTmpTwoArray = new int[2];
        View cardView = currentNotification.getNotificationView().findViewById(
                R.id.card_view);

        if (cardView == null) return;

        if (panelExpanded) {
            info.setTouchableInsets(
                    ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME);
            return;
        }

        cardView.getLocationOnScreen(mTmpTwoArray);
        int minX = mTmpTwoArray[0];
        int maxX = mTmpTwoArray[0] + cardView.getWidth();
        int height = cardView.getHeight();
        info.setTouchableInsets(
                ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
        info.touchableRegion.set(minX, mNotificationHeadsUpCardMarginTop, maxX,
                height + mNotificationHeadsUpCardMarginTop);
    }

    private void playSound(StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap) {
        NotificationListenerService.Ranking ranking = getRanking();
        if (rankingMap.getRanking(statusBarNotification.getKey(), ranking)) {
            NotificationChannel notificationChannel = ranking.getChannel();
            // If sound is not set on the notification channel and default is not chosen it
            // can be null.
            if (notificationChannel.getSound() != null) {
                // make the sound
                mBeeper.beep(statusBarNotification.getPackageName(),
                        notificationChannel.getSound());
            }
        }
    }

    private boolean shouldDismissOnSwipe(StatusBarNotification statusBarNotification) {
        return hasFullScreenIntent(statusBarNotification)
                && statusBarNotification.getNotification().category.equals(
                Notification.CATEGORY_CALL) && statusBarNotification.isOngoing();
    }


    @VisibleForTesting
    protected Map<String, HeadsUpEntry> getActiveHeadsUpNotifications() {
        return mActiveHeadsUpNotifications;
    }

    private void setAutoDismissViews(HeadsUpEntry currentNotification,
            StatusBarNotification statusBarNotification) {
        // Should not auto dismiss if HUN has a full screen Intent.
        if (hasFullScreenIntent(statusBarNotification)) {
            return;
        }
        currentNotification.getHandler().removeCallbacksAndMessages(null);
        currentNotification.getHandler().postDelayed(() -> animateOutHUN(statusBarNotification),
                mDuration);
    }

    /**
     * Returns true if StatusBarNotification has a full screen Intent.
     */
    private boolean hasFullScreenIntent(StatusBarNotification sbn) {
        return sbn.getNotification().fullScreenIntent != null;
    }

    /**
     * Animates the heads up notification out of the screen and reset the views.
     */
    private void animateOutHUN(StatusBarNotification statusBarNotification) {
        Log.d(TAG, "clearViews for Heads Up Notification: ");
        // get the current notification to perform animations and remove it immediately from the
        // active notification maps and cancel all other call backs if any.
        HeadsUpEntry currentHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        // view can also be removed when swipped away.
        if (currentHeadsUpNotification == null) {
            return;
        }
        currentHeadsUpNotification.getHandler().removeCallbacksAndMessages(null);
        currentHeadsUpNotification.getClickHandlerFactory().setHeadsUpNotificationCallBack(null);

        Interpolator exitInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.heads_up_exit_direction_interpolator);
        Interpolator alphaInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.heads_up_exit_alpha_interpolator);

        ObjectAnimator moveY = ObjectAnimator.ofFloat(
                currentHeadsUpNotification.getNotificationView(), "y",
                -1 * currentHeadsUpNotification.getNotificationView().getHeight());
        moveY.setDuration(mExitAnimationDuration);
        moveY.setInterpolator(exitInterpolator);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(
                currentHeadsUpNotification.getNotificationView(), "alpha", 1f);
        alpha.setDuration(mExitAnimationDuration);
        alpha.setInterpolator(alphaInterpolator);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(moveY, alpha);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeNotificationFromPanel(currentHeadsUpNotification);

                // Remove HUN after the animation ends to prevent accidental touch on the card
                // triggering another remove call.
                mActiveHeadsUpNotifications.remove(statusBarNotification.getKey());
            }
        });
        animatorSet.start();
    }

    /**
     * Remove notification from the screen. If it was the last notification hide the heads up panel.
     *
     * @param currentHeadsUpNotification The notification to remove
     */
    protected void removeNotificationFromPanel(HeadsUpEntry currentHeadsUpNotification) {
        mHeadsUpContentFrame.removeView(currentHeadsUpNotification.getNotificationView());
        if (mHeadsUpContentFrame.getChildCount() == 0) {
            mHeadsUpPanel.setVisibility(View.INVISIBLE);
        }
    }


    /**
     * Removes the view for the active heads up notification and also removes the HUN from the map
     * of active Notifications.
     */
    private void resetView(StatusBarNotification statusBarNotification) {
        HeadsUpEntry currentHeadsUpNotification = mActiveHeadsUpNotifications.get(
                statusBarNotification.getKey());
        if (currentHeadsUpNotification == null) return;

        currentHeadsUpNotification.getClickHandlerFactory().setHeadsUpNotificationCallBack(null);
        currentHeadsUpNotification.getHandler().removeCallbacksAndMessages(null);
        removeNotificationFromPanel(currentHeadsUpNotification);
        mActiveHeadsUpNotifications.remove(statusBarNotification.getKey());
    }

    /**
     * Choose a correct notification layout for this heads-up notification.
     * Note that the layout chosen can be different for the same notification
     * in the notification center.
     */
    @NotificationViewType
    private static int getNotificationViewType(StatusBarNotification statusBarNotification) {
        String category = statusBarNotification.getNotification().category;
        if (category != null) {
            switch (category) {
                case Notification.CATEGORY_CAR_EMERGENCY:
                    return NotificationViewType.CAR_EMERGENCY_HEADSUP;
                case Notification.CATEGORY_NAVIGATION:
                    return NotificationViewType.NAVIGATION;
                case Notification.CATEGORY_CALL:
                    return NotificationViewType.CALL;
                case Notification.CATEGORY_CAR_WARNING:
                    return NotificationViewType.CAR_WARNING_HEADSUP;
                case Notification.CATEGORY_CAR_INFORMATION:
                    return NotificationViewType.CAR_INFORMATION_HEADSUP;
                case Notification.CATEGORY_MESSAGE:
                    return NotificationViewType.MESSAGE_HEADSUP;
                default:
                    break;
            }
        }
        Bundle extras = statusBarNotification.getNotification().extras;
        if (extras.containsKey(Notification.EXTRA_BIG_TEXT)
                && extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) {
            return NotificationViewType.INBOX_HEADSUP;
        }
        // progress, media, big text, big picture, and basic templates
        return NotificationViewType.BASIC_HEADSUP;
    }

    /**
     * Helper method that determines whether a notification should show as a heads-up.
     *
     * <p> A notification will never be shown as a heads-up if:
     * <ul>
     * <li> Keyguard (lock screen) is showing
     * <li> OEMs configured CATEGORY_NAVIGATION should not be shown
     * <li> Notification is muted.
     * </ul>
     *
     * <p> A notification will be shown as a heads-up if:
     * <ul>
     * <li> Importance >= HIGH
     * <li> it comes from an app signed with the platform key.
     * <li> it comes from a privileged system app.
     * <li> is a car compatible notification.
     * {@link com.android.car.assist.client.CarAssistUtils#isCarCompatibleMessagingNotification}
     * <li> Notification category is one of CATEGORY_CALL or CATEGORY_NAVIGATION
     * </ul>
     *
     * <p> Group alert behavior still follows API documentation.
     *
     * @return true if a notification should be shown as a heads-up
     */
    private boolean shouldShowHeadsUp(
            StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap) {
        if (mKeyguardManager.isKeyguardLocked()) {
            return false;
        }
        Notification notification = statusBarNotification.getNotification();

        // Navigation notification configured by OEM
        if (!mEnableNavigationHeadsup && Notification.CATEGORY_NAVIGATION.equals(
                notification.category)) {
            return false;
        }
        // Group alert behavior
        if (notification.suppressAlertingDueToGrouping()) {
            return false;
        }
        // Messaging notification muted by user.
        if (mNotificationDataManager.isMessageNotificationMuted(statusBarNotification)) {
            return false;
        }

        // Do not show if importance < HIGH
        NotificationListenerService.Ranking ranking = getRanking();
        if (rankingMap.getRanking(statusBarNotification.getKey(), ranking)) {
            if (ranking.getImportance() < NotificationManager.IMPORTANCE_HIGH) {
                return false;
            }
        }

        if (NotificationUtils.isSystemPrivilegedOrPlatformKey(mContext,
                statusBarNotification)) {
            return true;
        }

        // Allow car messaging type.
        if (isCarCompatibleMessagingNotification(statusBarNotification)) {
            return true;
        }

        if (notification.category == null) {
            Log.d(TAG, "category not set for: " + statusBarNotification.getPackageName());
        }

        // Allow for Call, and nav TBT categories.
        if (Notification.CATEGORY_CALL.equals(notification.category)
                || Notification.CATEGORY_NAVIGATION.equals(notification.category)) {
            return true;
        }
        return false;
    }

    @VisibleForTesting
    protected NotificationListenerService.Ranking getRanking() {
        return new NotificationListenerService.Ranking();
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
        mShouldRestrictMessagePreview =
                (restrictions.getActiveRestrictions()
                        & CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE) != 0;
    }

    /**
     * Sets the source of {@link View.OnClickListener}
     *
     * @param clickHandlerFactory used to generate onClickListeners
     */
    @VisibleForTesting
    public void setClickHandlerFactory(NotificationClickHandlerFactory clickHandlerFactory) {
        mClickHandlerFactory = clickHandlerFactory;
    }

    /**
     * Callback that will be issued after a heads up notification is clicked
     */
    public interface Callback {
        /**
         * Clears Heads up notification on click.
         */
        void clearHeadsUpNotification();
    }
}
