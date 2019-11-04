package com.android.car.messenger;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.android.car.apps.common.LetterTileDrawable;
import com.android.car.messenger.bluetooth.BluetoothHelper;
import com.android.car.messenger.bluetooth.BluetoothMonitor;
import com.android.car.messenger.log.L;
import com.android.internal.annotations.GuardedBy;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Delegate class responsible for handling messaging service actions */
public class MessengerDelegate implements BluetoothMonitor.OnBluetoothEventListener {
    private static final String TAG = "CM.MessengerDelegate";
    // Static user name for building a MessagingStyle.
    private static final String STATIC_USER_NAME = "STATIC_USER_NAME";
    private static final Object mMapClientLock = new Object();

    private final Context mContext;
    @GuardedBy("mMapClientLock")
    private BluetoothMapClient mBluetoothMapClient;
    private NotificationManager mNotificationManager;
    private final SmsDatabaseHandler mSmsDatabaseHandler;
    private boolean mShouldLoadExistingMessages;

    @VisibleForTesting
    final Map<MessageKey, MapMessage> mMessages = new HashMap<>();
    @VisibleForTesting
    final Map<SenderKey, NotificationInfo> mNotificationInfos = new HashMap<>();
    // Mapping of when a device was connected via BluetoothMapClient. Used so we don't show
    // Notifications for messages received before this time.
    @VisibleForTesting
    final Map<String, Long> mBTDeviceAddressToConnectionTimestamp = new HashMap<>();

    public MessengerDelegate(Context context) {
        mContext = context;

        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mSmsDatabaseHandler = new SmsDatabaseHandler(mContext);

        try {
            mShouldLoadExistingMessages =
                    mContext.getResources().getBoolean(R.bool.config_loadExistingMessages);
        } catch(NotFoundException e) {
            // Should only happen for robolectric unit tests;
            L.e(TAG, e, "Disabling loading of existing messages");
            mShouldLoadExistingMessages = false;
        }
    }

    @Override
    public void onMessageReceived(Intent intent) {
        try {
            MapMessage message = MapMessage.parseFrom(intent);

            MessageKey messageKey = new MessageKey(message);
            boolean repeatMessage = mMessages.containsKey(messageKey);
            mMessages.put(messageKey, message);
            if (!repeatMessage) {
                mSmsDatabaseHandler.addOrUpdate(message);
                updateNotification(messageKey, message);
            }
        } catch (IllegalArgumentException e) {
            L.e(TAG, e, "Dropping invalid MAP message.");
        }
    }

    @Override
    public void onMessageSent(Intent intent) {
        /* NO-OP */
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        L.d(TAG, "Device connected: \t%s", device.getAddress());
        mBTDeviceAddressToConnectionTimestamp.put(device.getAddress(), System.currentTimeMillis());
        synchronized (mMapClientLock) {
            if (mBluetoothMapClient != null) {
                if (mShouldLoadExistingMessages) {
                    mBluetoothMapClient.getUnreadMessages(device);
                }
            } else {
                // onDeviceConnected should be sent by BluetoothMapClient, so log if we run into
                // this strange case.
                L.e(TAG, "BluetoothMapClient is null after connecting to device.");
            }
        }
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        L.d(TAG, "Device disconnected: \t%s", device.getAddress());
        cleanupMessagesAndNotifications(key -> key.matches(device.getAddress()));
        mBTDeviceAddressToConnectionTimestamp.remove(device.getAddress());
        mSmsDatabaseHandler.removeMessagesForDevice(device.getAddress());
    }

    @Override
    public void onMapConnected(BluetoothMapClient client) {
        List<BluetoothDevice> connectedDevices;
        synchronized (mMapClientLock) {
            if (mBluetoothMapClient == client) {
                return;
            }

            if (mBluetoothMapClient != null) {
                mBluetoothMapClient.close();
            }

            mBluetoothMapClient = client;
            connectedDevices = mBluetoothMapClient.getConnectedDevices();
        }
        if (connectedDevices != null) {
            for (BluetoothDevice device : connectedDevices) {
                onDeviceConnected(device);
            }
        }
    }

    @Override
    public void onMapDisconnected(int profile) {
        cleanupMessagesAndNotifications(key -> true);
        synchronized (mMapClientLock) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                adapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mBluetoothMapClient);
            }
            mBluetoothMapClient = null;
        }
    }

    @Override
    public void onSdpRecord(BluetoothDevice device, boolean supportsReply) {
        /* NO_OP */
    }

    protected void sendMessage(SenderKey senderKey, String messageText) {
        boolean success = false;
        // Even if the device is not connected, try anyway so that the reply in enqueued.
        synchronized (mMapClientLock) {
            if (mBluetoothMapClient != null) {
                NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
                if (notificationInfo == null) {
                    L.w(TAG, "No notificationInfo found for senderKey: %s", senderKey);
                } else if (notificationInfo.mSenderContactUri == null) {
                    L.w(TAG, "Do not have contact URI for sender!");
                } else {
                    Uri[] recipientUris = {Uri.parse(notificationInfo.mSenderContactUri)};

                    final int requestCode = senderKey.hashCode();

                    Intent intent = new Intent(BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY);
                    PendingIntent sentIntent = PendingIntent.getBroadcast(mContext, requestCode,
                            intent,
                            PendingIntent.FLAG_ONE_SHOT);

                    success = BluetoothHelper.sendMessage(mBluetoothMapClient,
                            senderKey.getDeviceAddress(), recipientUris, messageText,
                            sentIntent, null);
                }
            }
        }

        final boolean deviceConnected = mBTDeviceAddressToConnectionTimestamp.containsKey(
                senderKey.getDeviceAddress());
        if (!success || !deviceConnected) {
            L.e(TAG, "Unable to send reply!");
            final int toastResource = deviceConnected
                    ? R.string.auto_reply_failed_message
                    : R.string.auto_reply_device_disconnected;

            Toast.makeText(mContext, toastResource, Toast.LENGTH_SHORT).show();
        }
    }

    protected void markAsRead(SenderKey senderKey) {
        NotificationInfo info = mNotificationInfos.get(senderKey);
        for (MessageKey key : info.mMessageKeys) {
            MapMessage message = mMessages.get(key);
            if (!message.isReadOnCar()) {
                message.markMessageAsRead();
                mSmsDatabaseHandler.addOrUpdate(message);
            }
        }
    }

    /**
     * Clears all notifications matching the {@param predicate}. Example method calls are when user
     * wants to clear (a) message notification(s), or when the Bluetooth device that received the
     * messages has been disconnected.
     */
    protected void clearNotifications(Predicate<CompositeKey> predicate) {
        mNotificationInfos.forEach((senderKey, notificationInfo) -> {
            if (predicate.test(senderKey)) {
                mNotificationManager.cancel(notificationInfo.mNotificationId);
            }
        });
    }

    /** Removes all messages related to the inputted predicate, and cancels their notifications. **/
    private void cleanupMessagesAndNotifications(Predicate<CompositeKey> predicate) {
        for (MessageKey key : mMessages.keySet()) {
            if (predicate.test(key)) {
                mSmsDatabaseHandler.removeMessagesForDevice(key.getDeviceAddress());
            }
        }
        mMessages.entrySet().removeIf(
                messageKeyMapMessageEntry -> predicate.test(messageKeyMapMessageEntry.getKey()));
        clearNotifications(predicate);
        mNotificationInfos.entrySet().removeIf(entry -> predicate.test(entry.getKey()));
    }

    private void updateNotification(MessageKey messageKey, MapMessage mapMessage) {
        // Only show notifications for messages received AFTER phone was connected.
        if (mapMessage.getReceiveTime()
                < mBTDeviceAddressToConnectionTimestamp.get(mapMessage.getDeviceAddress())) {
            return;
        }

        SmsDatabaseHandler.readDatabase(mContext);
        SenderKey senderKey = new SenderKey(mapMessage);
        if (!mNotificationInfos.containsKey(senderKey)) {
            mNotificationInfos.put(senderKey, new NotificationInfo(mapMessage.getSenderName(),
                    mapMessage.getSenderContactUri()));
        }
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        notificationInfo.mMessageKeys.add(messageKey);

        updateNotification(senderKey, notificationInfo);
    }

    private void updateNotification(SenderKey senderKey, NotificationInfo notificationInfo) {
        final Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                getContactId(mContext.getContentResolver(), notificationInfo.mSenderContactUri));

        Glide.with(mContext)
                .asBitmap()
                .load(photoUri)
                .apply(RequestOptions.circleCropTransform())
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap,
                            Transition<? super Bitmap> transition) {
                        sendNotification(bitmap);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable fallback) {
                        sendNotification(null);
                    }

                    private void sendNotification(Bitmap bitmap) {
                        mNotificationManager.notify(
                                notificationInfo.mNotificationId,
                                createNotification(senderKey, notificationInfo, bitmap));
                    }
                });
    }

    // TODO: move out to a shared library.
    protected static int getContactId(ContentResolver cr, String contactUri) {
        if (TextUtils.isEmpty(contactUri)) {
            return 0;
        }

        Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(contactUri));
        String[] projection = new String[]{ContactsContract.PhoneLookup._ID};

        try (Cursor cursor = cr.query(lookupUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.isLast()) {
                return cursor.getInt(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
            } else {
                L.w(TAG, "Unable to find contact id from phone number.");
            }
        }

        return 0;
    }

    protected void cleanup() {
        cleanupMessagesAndNotifications(key -> true);
        synchronized (mMapClientLock) {
            if (mBluetoothMapClient != null) {
                mBluetoothMapClient.close();
            }
        }
    }

    private Notification createNotification(
            SenderKey senderKey, NotificationInfo notificationInfo, Bitmap bitmap) {
        String contentText = mContext.getResources().getQuantityString(
                R.plurals.notification_new_message, notificationInfo.mMessageKeys.size(),
                notificationInfo.mMessageKeys.size());
        long lastReceiveTime = mMessages.get(notificationInfo.mMessageKeys.getLast())
                .getReceiveTime();

        if (bitmap == null) {
            bitmap = letterTileBitmap(notificationInfo.mSenderName);
        }

        final String senderName = notificationInfo.mSenderName;
        final int notificationId = notificationInfo.mNotificationId;

        // Create the Content Intent
        PendingIntent deleteIntent = createServiceIntent(senderKey, notificationId,
                MessengerService.ACTION_CLEAR_NOTIFICATION_STATE);

        List<Action> actions = getNotificationActions(senderKey, notificationId);

        Person user = new Person.Builder()
                .setName(STATIC_USER_NAME)
                .build();
        MessagingStyle messagingStyle = new MessagingStyle(user);
        Person sender = new Person.Builder()
                .setName(senderName)
                .setUri(notificationInfo.mSenderContactUri)
                .build();
        notificationInfo.mMessageKeys.stream().map(mMessages::get).forEachOrdered(message -> {
            if (!message.isReadOnCar()) {
                messagingStyle.addMessage(
                        message.getMessageText(),
                        message.getReceiveTime(),
                        sender);
            }
        });

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext,
                MessengerService.SMS_CHANNEL_ID)
                .setContentTitle(senderName)
                .setContentText(contentText)
                .setStyle(messagingStyle)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setLargeIcon(bitmap)
                .setSmallIcon(R.drawable.ic_message)
                .setWhen(lastReceiveTime)
                .setShowWhen(true)
                .setDeleteIntent(deleteIntent);

        for (final Action action : actions) {
            builder.addAction(action);
        }

        return builder.build();
    }

    private Bitmap letterTileBitmap(String senderName) {
        LetterTileDrawable letterTileDrawable = new LetterTileDrawable(mContext.getResources());
        letterTileDrawable.setContactDetails(senderName, senderName);
        letterTileDrawable.setIsCircular(true);

        int bitmapSize = mContext.getResources()
                .getDimensionPixelSize(R.dimen.notification_contact_photo_size);

        return letterTileDrawable.toBitmap(bitmapSize);
    }

    private PendingIntent createServiceIntent(SenderKey senderKey, int notificationId,
            String action) {
        Intent intent = new Intent(mContext, MessengerService.class)
                .setAction(action)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, senderKey);

        return PendingIntent.getForegroundService(mContext, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private List<Action> getNotificationActions(SenderKey senderKey, int notificationId) {

        final int icon = android.R.drawable.ic_media_play;

        final List<Action> actionList = new ArrayList<>();

        // Reply action
        if (shouldAddReplyAction(senderKey.getDeviceAddress())) {
            final String replyString = mContext.getString(R.string.action_reply);
            PendingIntent replyIntent = createServiceIntent(senderKey, notificationId,
                    MessengerService.ACTION_VOICE_REPLY);
            actionList.add(
                    new Action.Builder(icon, replyString, replyIntent)
                            .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                            .setShowsUserInterface(false)
                            .addRemoteInput(
                                    new RemoteInput.Builder(MessengerService.REMOTE_INPUT_KEY)
                                            .build()
                            )
                            .build()
            );
        }

        // Mark-as-read Action. This will be the callback of Notification Center's "Read" action.
        final String markAsRead = mContext.getString(R.string.action_mark_as_read);
        PendingIntent markAsReadIntent = createServiceIntent(senderKey, notificationId,
                MessengerService.ACTION_MARK_AS_READ);
        actionList.add(
                new Action.Builder(icon, markAsRead, markAsReadIntent)
                        .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build()
        );

        return actionList;
    }

    private boolean shouldAddReplyAction(String deviceAddress) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        }
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);

        synchronized (mMapClientLock) {
            return (mBluetoothMapClient != null) && mBluetoothMapClient.isUploadingSupported(
                    device);
        }
    }

    /**
     * Contains information about a single notification that is displayed, with grouped messages.
     */
    @VisibleForTesting
    static class NotificationInfo {
        private static int NEXT_NOTIFICATION_ID = 0;

        final int mNotificationId = NEXT_NOTIFICATION_ID++;
        final String mSenderName;
        @Nullable
        final String mSenderContactUri;
        final LinkedList<MessageKey> mMessageKeys = new LinkedList<>();

        NotificationInfo(String senderName, @Nullable String senderContactUri) {
            mSenderName = senderName;
            mSenderContactUri = senderContactUri;
        }
    }

    /**
     * {@link CompositeKey} subclass used to identify specific messages; it uses message-handle as
     * the secondary key.
     */
    public static class MessageKey extends CompositeKey {
        MessageKey(MapMessage message) {
            super(message.getDeviceAddress(), message.getHandle());
        }
    }
}
