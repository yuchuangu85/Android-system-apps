/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock.data

/**
 * Data that must be coordinated across all notifications is accessed via this model.
 */
internal class NotificationModel {
    /**
     * @return `true` while the application is open in the foreground
     */
    /**
     * @param inForeground `true` to indicate the application is open in the foreground
     */
    var isApplicationInForeground = false

    //
    // Notification IDs
    //
    // Used elsewhere:
    // Integer.MAX_VALUE - 4
    // Integer.MAX_VALUE - 5
    // Integer.MAX_VALUE - 7
    //

    /**
     * @return a value that identifies the stopwatch notification
     */
    val stopwatchNotificationId: Int
        get() = Int.MAX_VALUE - 1

    /**
     * @return a value that identifies the notification for running/paused timers
     */
    val unexpiredTimerNotificationId: Int
        get() = Int.MAX_VALUE - 2

    /**
     * @return a value that identifies the notification for expired timers
     */
    val expiredTimerNotificationId: Int
        get() = Int.MAX_VALUE - 3

    /**
     * @return a value that identifies the notification for missed timers
     */
    val missedTimerNotificationId: Int
        get() = Int.MAX_VALUE - 6

    //
    // Notification Group keys
    //
    // Used elsewhere:
    // "1"
    // "4"

    /**
     * @return the group key for the stopwatch notification
     */
    val stopwatchNotificationGroupKey: String
        get() = "3"

    /**
     * @return the group key for the timer notification
     */
    val timerNotificationGroupKey: String
        get() = "2"

    //
    // Notification Sort keys
    //

    /**
     * @return the sort key for the timer notification
     */
    val timerNotificationSortKey: String
        get() = "0"

    /**
     * @return the sort key for the missed timer notification
     */
    val timerNotificationMissedSortKey: String
        get() = "1"
}