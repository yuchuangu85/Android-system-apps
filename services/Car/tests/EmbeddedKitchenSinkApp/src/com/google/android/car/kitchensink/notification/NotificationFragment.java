package com.google.android.car.kitchensink.notification;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.HashMap;

/**
 * Test fragment that can send all sorts of notifications.
 */
public class NotificationFragment extends Fragment {
    private static final String IMPORTANCE_HIGH_ID = "importance_high";
    private static final String IMPORTANCE_HIGH_NO_SOUND_ID = "importance_high_no_sound";
    private static final String IMPORTANCE_DEFAULT_ID = "importance_default";
    private static final String IMPORTANCE_LOW_ID = "importance_low";
    private static final String IMPORTANCE_MIN_ID = "importance_min";
    private static final String IMPORTANCE_NONE_ID = "importance_none";
    private int mCurrentNotificationId = 0;
    private NotificationManager mManager;
    private Context mContext;
    private Handler mHandler = new Handler();
    private HashMap<Integer, Runnable> mUpdateRunnables = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_HIGH_ID, "Importance High", NotificationManager.IMPORTANCE_HIGH));

        NotificationChannel noSoundChannel = new NotificationChannel(
                IMPORTANCE_HIGH_NO_SOUND_ID, "No sound", NotificationManager.IMPORTANCE_HIGH);
        noSoundChannel.setSound(null, null);
        mManager.createNotificationChannel(noSoundChannel);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_DEFAULT_ID,
                "Importance Default",
                NotificationManager.IMPORTANCE_DEFAULT));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_LOW_ID, "Importance Low", NotificationManager.IMPORTANCE_LOW));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_MIN_ID, "Importance Min", NotificationManager.IMPORTANCE_MIN));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_NONE_ID, "Importance None", NotificationManager.IMPORTANCE_NONE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.notification_fragment, container, false);

        initCancelAllButton(view);

        initCarCategoriesButton(view);

        initImportanceHighBotton(view);
        initImportanceDefaultButton(view);
        initImportanceLowButton(view);
        initImportanceMinButton(view);

        initOngoingButton(view);
        initMessagingStyleButton(view);
        initTestMessagesButton(view);
        initProgressButton(view);
        initNavigationButton(view);
        initMediaButton(view);
        initCallButton(view);

        return view;
    }

    private PendingIntent createServiceIntent(int notificationId, String action) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class).setAction(action);

        return PendingIntent.getForegroundService(mContext, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initCancelAllButton(View view) {
        view.findViewById(R.id.cancel_all_button).setOnClickListener(v -> {
            for (Runnable runnable : mUpdateRunnables.values()) {
                mHandler.removeCallbacks(runnable);
            }
            mUpdateRunnables.clear();
            mManager.cancelAll();
        });
    }

    private void initCarCategoriesButton(View view) {
        view.findViewById(R.id.category_car_emergency_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Car Emergency")
                    .setContentText("Shows heads-up; Shows on top of the list; Does not group")
                    .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_warning_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Car Warning")
                    .setContentText(
                            "Shows heads-up; Shows on top of the list but below Car Emergency; "
                                    + "Does not group")
                    .setCategory(Notification.CATEGORY_CAR_WARNING)
                    .setColor(mContext.getColor(android.R.color.holo_orange_dark))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_info_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Car information")
                    .setContentText("Doesn't show heads-up; Importance Default; Groups")
                    .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                    .setColor(mContext.getColor(android.R.color.holo_orange_light))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

    }

    private void initImportanceHighBotton(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

        Notification notification1 = new Notification
                .Builder(mContext, IMPORTANCE_HIGH_ID)
                .setContentTitle("Importance High: Shows as a heads-up")
                .setContentText(
                        "Each click generates a new notification. And some "
                                + "looooooong text. "
                                + "Loooooooooooooooooooooong. "
                                + "Loooooooooooooooooooooooooooooooooooooooooooooooooong.")
                .setSmallIcon(R.drawable.car_ic_mode)
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", pendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Action (no-op)", pendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", pendingIntent).build())
                .setColor(mContext.getColor(android.R.color.holo_red_light))
                .build();

        view.findViewById(R.id.importance_high_button).setOnClickListener(
                v -> mManager.notify(mCurrentNotificationId++, notification1)
        );
    }

    private void initImportanceDefaultButton(View view) {
        view.findViewById(R.id.importance_default_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("No heads-up; Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceLowButton(View view) {
        view.findViewById(R.id.importance_low_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_LOW_ID)
                    .setContentTitle("Importance Low")
                    .setContentText("No heads-up; Below Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceMinButton(View view) {
        view.findViewById(R.id.importance_min_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_MIN_ID)
                    .setContentTitle("Importance Min")
                    .setContentText("No heads-up; Below Importance Low; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initOngoingButton(View view) {
        view.findViewById(R.id.ongoing_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Persistent/Ongoing Notification")
                    .setContentText("Cannot be dismissed; No heads-up; Importance default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setOngoing(true)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initMessagingStyleButton(View view) {
        view.findViewById(R.id.category_message_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person person1 = new Person.Builder()
                    .setName("Person " + id)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.avatar1))
                    .build();
            Person person2 = new Person.Builder()
                    .setName("Person " + id + 1)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.android_logo))
                    .build();
            Person person3 = new Person.Builder()
                    .setName("Person " + id + 2)
                    .setIcon(IconCompat.createWithResource(v.getContext(), R.drawable.avatar2))
                    .build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person3)
                            .setConversationTitle("Group chat")
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person1.getName() + "'s message",
                                            System.currentTimeMillis(),
                                            person1))
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person2.getName() + "'s message",
                                            System.currentTimeMillis(),
                                            person2))
                            .addMessage(
                                    new MessagingStyle.Message(
                                            person3.getName() + "'s message; "
                                                    + "Each click generates a new"
                                                    + "notification. And some looooooong text. "
                                                    + "Loooooooooooooooooooooong. "
                                                    + "Loooooooooooooooooooooooooong."
                                                    + "Long long long long text.",
                                            System.currentTimeMillis(),
                                            person3));

            NotificationCompat.Builder notification = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Jane, John, Joe")
                    .setContentText("Group chat")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            mManager.notify(id, notification.build());
        });
    }

    private void initTestMessagesButton(View view) {
        view.findViewById(R.id.test_message_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person person = new Person.Builder().setName("John Doe").build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(person).setConversationTitle("Hello!");
            NotificationCompat.Builder builder = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("hi")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            Runnable runnable = new Runnable() {
                int mCount = 1;

                @Override
                public void run() {
                    NotificationCompat.Builder updateNotification =
                            builder.setStyle(messagingStyle.addMessage(
                                    new MessagingStyle.Message(
                                            "Message " + mCount++,
                                            System.currentTimeMillis(),
                                            person)));
                    mManager.notify(id, updateNotification.build());
                    if (mCount < 5) {
                        mHandler.postDelayed(this, 6000);
                    }
                }
            };
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private void initProgressButton(View view) {
        view.findViewById(R.id.progress_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Progress")
                    .setOngoing(true)
                    .setContentText(
                            "Doesn't show heads-up; Importance Default; Groups; Ongoing (cannot "
                                    + "be dismissed)")
                    .setProgress(100, 0, false)
                    .setColor(mContext.getColor(android.R.color.holo_purple))
                    .setContentInfo("0%")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(id, notification);

            Runnable runnable = new Runnable() {
                int mProgress = 0;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                            .setContentTitle("Progress")
                            .setContentText("Doesn't show heads-up; Importance Default; Groups")
                            .setProgress(100, mProgress, false)
                            .setOngoing(true)
                            .setColor(mContext.getColor(android.R.color.holo_purple))
                            .setContentInfo(mProgress + "%")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .build();
                    mManager.notify(id, updateNotification);
                    mProgress += 5;
                    if (mProgress <= 100) {
                        mHandler.postDelayed(this, 1000);
                    }
                }
            };
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private void initNavigationButton(View view) {
        view.findViewById(R.id.navigation_button).setOnClickListener(v -> {

            int id1 = mCurrentNotificationId++;
            Runnable rightTurnRunnable = new Runnable() {
                int mDistance = 900;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_ID)
                            .setCategory("navigation")
                            .setContentTitle("Navigation")
                            .setContentText("Turn right in " + mDistance + " ft")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " ft")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .setOnlyAlertOnce(true)
                            .build();
                    mManager.notify(id1, updateNotification);
                    mDistance -= 100;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 1000);
                    } else {
                        mManager.cancel(id1);
                    }
                }
            };
            mUpdateRunnables.put(id1, rightTurnRunnable);
            mHandler.postDelayed(rightTurnRunnable, 1000);

            int id2 = mCurrentNotificationId++;
            Runnable exitRunnable = new Runnable() {
                int mDistance = 20;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_ID)
                            .setCategory("navigation")
                            .setContentTitle("Navigation")
                            .setContentText("Exit in " + mDistance + " miles")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " miles")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .setOnlyAlertOnce(true)
                            .build();
                    mManager.notify(id2, updateNotification);
                    mDistance -= 1;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 500);
                    }
                }
            };
            mUpdateRunnables.put(id2, exitRunnable);
            mHandler.postDelayed(exitRunnable, 10000);
        });
    }

    private void initMediaButton(View view) {
        view.findViewById(R.id.media_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification.Builder builder = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Lady Adora")
                    .setContentText("Funny Face")
                    .setColor(mContext.getColor(android.R.color.holo_orange_dark))
                    .setColorized(true)
                    .setSubText("Some album")
                    .addAction(new Notification.Action(R.drawable.thumb_down, "Thumb down", null))
                    .addAction(new Notification.Action(R.drawable.skip_prev, "Skip prev", null))
                    .addAction(new Notification.Action(R.drawable.play_arrow, "Play", null))
                    .addAction(new Notification.Action(R.drawable.skip_next, "Skip next", null))
                    .addAction(new Notification.Action(R.drawable.thumb_up, "Thumb up", null))
                    .setSmallIcon(R.drawable.play_arrow)
                    .setLargeIcon(Icon.createWithResource(mContext, R.drawable.android_logo));

            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setShowActionsInCompactView(1, 2, 3);
            MediaSession mediaSession = new MediaSession(mContext, "KitchenSink");
            style.setMediaSession(mediaSession.getSessionToken());
            builder.setStyle(style);
            mediaSession.release();

            mManager.notify(id, builder.build());
        });
    }

    private void initCallButton(View view) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

        view.findViewById(R.id.category_call_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("+1 1231231234")
                    .setContentText("Shows persistent heads-up")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setFullScreenIntent(pendingIntent, true)
                    .setColor(mContext.getColor(android.R.color.holo_red_light))
                    .setColorized(true)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }
}
