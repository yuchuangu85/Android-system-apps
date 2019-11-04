package com.android.car.notification;

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.List;

/**
 * This class is a bridge to collect signals from the notification and ux restriction services and
 * trigger the correct UI updates.
 */
public class NotificationViewController {

    private static final String TAG = "NotificationViewControl";
    private final CarNotificationView mCarNotificationView;
    private final PreprocessingManager mPreprocessingManager;
    private final CarNotificationListener mCarNotificationListener;
    private CarUxRestrictionManagerWrapper mUxResitrictionListener;
    private NotificationDataManager mNotificationDataManager;
    private NotificationUpdateHandler mNotificationUpdateHandler = new NotificationUpdateHandler();
    private boolean mShowLessImportantNotifications;
    private boolean mIsInForeground;

    public NotificationViewController(CarNotificationView carNotificationView,
            PreprocessingManager preprocessingManager,
            CarNotificationListener carNotificationListener,
            CarUxRestrictionManagerWrapper uxResitrictionListener,
            NotificationDataManager notificationDataManager) {
        mCarNotificationView = carNotificationView;
        mPreprocessingManager = preprocessingManager;
        mCarNotificationListener = carNotificationListener;
        mUxResitrictionListener = uxResitrictionListener;
        mNotificationDataManager = notificationDataManager;

        // Long clicking on the notification center title toggles hiding media, navigation, and
        // less important (< IMPORTANCE_DEFAULT) ongoing foreground service notifications.
        // This is only available for ENG and USERDEBUG builds.
        View view = mCarNotificationView.findViewById(R.id.notification_center_title);
        if (view != null && (Build.IS_ENG || Build.IS_USERDEBUG)) {
            view.setOnLongClickListener(v -> {
                mShowLessImportantNotifications = !mShowLessImportantNotifications;
                Toast.makeText(
                        carNotificationView.getContext(),
                        "Foreground, navigation and media notifications " + (
                                mShowLessImportantNotifications ? "ENABLED" : "DISABLED"),
                        Toast.LENGTH_SHORT).show();
                resetNotifications(mShowLessImportantNotifications);
                return true;
            });
        }
    }

    /**
     * Updates UI and registers required listeners
     */
    public void enable() {
        mCarNotificationListener.setHandler(mNotificationUpdateHandler);
        mUxResitrictionListener.setCarNotificationView(mCarNotificationView);
        try {
            CarUxRestrictions currentRestrictions =
                    mUxResitrictionListener.getCurrentCarUxRestrictions();
            mCarNotificationView.onUxRestrictionsChanged(currentRestrictions);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    /**
     * Remove listeners.
     */
    public void disable() {
        mCarNotificationListener.setHandler(null);
        mUxResitrictionListener.setCarNotificationView(null);
    }

    /**
     * Reset the list view. Called when the notification list is not in the foreground.
     */
    public void setIsInForeground(boolean isInForeground) {
        mIsInForeground = isInForeground;
        // Reset and collapse all groups when notification view disappears.
        if (!mIsInForeground) {
            resetNotifications(mShowLessImportantNotifications);
            mCarNotificationView.collapseAllGroups();
        }
    }

    /**
     * Reset notifications to the latest state.
     */
    private void resetNotifications(boolean showLessImportantNotifications) {
        mPreprocessingManager.init(
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        List<NotificationGroup> notificationGroups = mPreprocessingManager.process(
                showLessImportantNotifications,
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        mNotificationDataManager.updateUnseenNotification(notificationGroups);
        mCarNotificationView.setNotifications(notificationGroups);
    }

    /**
     * Update notifications: no grouping/ranking updates will go through.
     * Insertion, deletion and content update will apply immediately.
     */
    private void updateNotifications(
            boolean showLessImportantNotifications, int what, StatusBarNotification sbn) {

        if (mPreprocessingManager.shouldFilter(sbn, mCarNotificationListener.getCurrentRanking())) {
            // if the new notification should be filtered out, return early
            return;
        }

        mCarNotificationView.setNotifications(
                mPreprocessingManager.updateNotifications(
                        showLessImportantNotifications,
                        sbn,
                        what,
                        mCarNotificationListener.getCurrentRanking()));
    }

    private class NotificationUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (mIsInForeground) {
                updateNotifications(
                        mShowLessImportantNotifications,
                        message.what,
                        (StatusBarNotification) message.obj);
            } else {
                resetNotifications(mShowLessImportantNotifications);
            }
        }
    }
}
