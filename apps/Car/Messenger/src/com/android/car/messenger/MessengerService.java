package com.android.car.messenger;


import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.android.car.messenger.bluetooth.BluetoothMonitor;
import com.android.car.messenger.log.L;

/** Service responsible for handling SMS messaging events from paired Bluetooth devices. */
public class MessengerService extends Service {
    private final static String TAG = "CM.MessengerService";

    /* ACTIONS */
    /** Used to start this service at boot-complete. Takes no arguments. */
    public static final String ACTION_START = "com.android.car.messenger.ACTION_START";

    /** Used to reply to message with voice input; triggered by an assistant. */
    public static final String ACTION_VOICE_REPLY = "com.android.car.messenger.ACTION_VOICE_REPLY";

    /** Used to clear notification state when user dismisses notification. */
    public static final String ACTION_CLEAR_NOTIFICATION_STATE =
            "com.android.car.messenger.ACTION_CLEAR_NOTIFICATION_STATE";

    /** Used to mark a notification as read **/
    public static final String ACTION_MARK_AS_READ =
            "com.android.car.messenger.ACTION_MARK_AS_READ";

    /** Used to notify when a sms is received. Takes no arguments. */
    public static final String ACTION_RECEIVED_SMS =
            "com.android.car.messenger.ACTION_RECEIVED_SMS";

    /** Used to notify when a mms is received. Takes no arguments. */
    public static final String ACTION_RECEIVED_MMS =
            "com.android.car.messenger.ACTION_RECEIVED_MMS";

    /* EXTRAS */
    /** Key under which the {@link SenderKey} is provided. */
    public static final String EXTRA_SENDER_KEY = "com.android.car.messenger.EXTRA_SENDER_KEY";

    /**
     * The resultKey of the {@link RemoteInput} which is sent in the reply callback {@link Action}.
     */
    public static final String REMOTE_INPUT_KEY = "REMOTE_INPUT_KEY";

    /* NOTIFICATIONS */
    static final String SMS_CHANNEL_ID = "SMS_CHANNEL_ID";
    private static final String APP_RUNNING_CHANNEL_ID = "APP_RUNNING_CHANNEL_ID";
    private static final int SERVICE_STARTED_NOTIFICATION_ID = Integer.MAX_VALUE;

    /** Delegate class used to handle this services' actions */
    private MessengerDelegate mMessengerDelegate;

    /** Notifies this service of new bluetooth actions */
    private BluetoothMonitor mBluetoothMonitor;

    /* Binding boilerplate */
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        MessengerService getService() {
            return MessengerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.d(TAG, "onCreate");

        mMessengerDelegate = new MessengerDelegate(this);
        mBluetoothMonitor = new BluetoothMonitor(this);
        mBluetoothMonitor.registerListener(mMessengerDelegate);
        sendServiceRunningNotification();
    }


    private void sendServiceRunningNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            L.e(TAG, "Failed to get NotificationManager instance");
            return;
        }

        // Create notification channel for app running notification
        {
            NotificationChannel appRunningNotificationChannel =
                    new NotificationChannel(APP_RUNNING_CHANNEL_ID,
                            getString(R.string.app_running_msg_channel_name),
                            NotificationManager.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(appRunningNotificationChannel);
        }

        {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            NotificationChannel smsChannel = new NotificationChannel(SMS_CHANNEL_ID,
                    getString(R.string.sms_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            smsChannel.setDescription(getString(R.string.sms_channel_description));
            smsChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes);
            notificationManager.createNotificationChannel(smsChannel);
        }

        final Notification notification =
                new NotificationCompat.Builder(this, APP_RUNNING_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_message)
                        .setContentTitle(getString(R.string.app_running_msg_notification_title))
                        .setContentText(getString(R.string.app_running_msg_notification_content))
                        .build();
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        L.d(TAG, "onDestroy");
        mMessengerDelegate.cleanup();
        mBluetoothMonitor.cleanup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int result = START_STICKY;

        if (intent == null || intent.getAction() == null) return result;

        final String action = intent.getAction();

        if (!hasRequiredArgs(intent)) {
            L.e(TAG, "Dropping command: %s. Reason: Missing required argument.", action);
            return result;
        }

        switch (action) {
            case ACTION_START:
                // NO-OP
                break;
            case ACTION_VOICE_REPLY:
                voiceReply(intent);
                break;
            case ACTION_CLEAR_NOTIFICATION_STATE:
                clearNotificationState(intent);
                break;
            case ACTION_MARK_AS_READ:
                markAsRead(intent);
                break;
            case ACTION_RECEIVED_SMS:
                // NO-OP
                break;
            case ACTION_RECEIVED_MMS:
                // NO-OP
                break;
            case TelephonyManager.ACTION_RESPOND_VIA_MESSAGE:
                respondViaMessage(intent);
                break;
            default:
                L.w(TAG, "Unsupported action: %s", action);
        }

        return result;
    }

    /**
     * Checks that the intent has all of the required arguments for its requested action.
     *
     * @param intent the intent to check
     * @return true if the intent has all of the required {@link Bundle} args for its action
     */
    private static boolean hasRequiredArgs(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_VOICE_REPLY:
            case ACTION_CLEAR_NOTIFICATION_STATE:
            case ACTION_MARK_AS_READ:
                if (!intent.hasExtra(EXTRA_SENDER_KEY)) {
                    L.w(TAG, "Intent %s missing sender-key extra.", intent.getAction());
                    return false;
                }
                return true;
            default:
                // For unknown actions, default to true. We'll report an error for these later.
                return true;
        }
    }

    /**
     * Sends a reply, meant to be used from a caller originating from voice input.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} and
     *               a {@link RemoteInput} with {@link MessengerService#REMOTE_INPUT_KEY} resultKey
     */
    public void voiceReply(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        final Bundle bundle = RemoteInput.getResultsFromIntent(intent);
        if (bundle == null) {
            L.e(TAG, "Dropping voice reply. Received null RemoteInput result!");
            return;
        }
        final CharSequence message = bundle.getCharSequence(REMOTE_INPUT_KEY);
        L.d(TAG, "voiceReply");
        if (!TextUtils.isEmpty(message)) {
            mMessengerDelegate.sendMessage(senderKey, message.toString());
        }
    }

    /**
     * Clears notification(s) associated with a given sender key.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void clearNotificationState(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "clearNotificationState");
        mMessengerDelegate.clearNotifications(key -> key.equals(senderKey));
    }

    /**
     * Mark a conversation associated with a given sender key as read.
     *
     * @param intent intent containing {@link MessengerService#EXTRA_SENDER_KEY} bundle argument
     */
    public void markAsRead(Intent intent) {
        final SenderKey senderKey = intent.getParcelableExtra(EXTRA_SENDER_KEY);
        L.d(TAG, "markAsRead");
        mMessengerDelegate.markAsRead(senderKey);
    }

    /**
     * Respond to a call via text message.
     *
     * @param intent intent containing a URI describing the recipient and the URI schema
     */
    public void respondViaMessage(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            L.v(TAG, "Called to send SMS but no extras");
            return;
        }

        // TODO: get senderKey from the recipient's address, and sendMessage() to it.
    }
}
