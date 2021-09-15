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

package com.android.deskclock.controller

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes

import com.android.deskclock.Utils
import com.android.deskclock.events.EventTracker

/**
 * Interactions with Android framework components responsible for part of the user experience are
 * handled via this singleton.
 */
class Controller private constructor() {
    private var mContext: Context? = null

    /** The controller that dispatches app events to event trackers.  */
    private lateinit var mEventController: EventController

    /** The controller that interacts with voice interaction sessions on M+.  */
    private lateinit var mVoiceController: VoiceController

    /** The controller that creates and updates launcher shortcuts on N MR1+  */
    private var mShortcutController: ShortcutController? = null

    fun setContext(context: Context) {
        if (mContext != context) {
            mContext = context.getApplicationContext()
            mEventController = EventController()
            mVoiceController = VoiceController()
            if (Utils.isNMR1OrLater) {
                mShortcutController = ShortcutController(mContext!!)
            }
        }
    }

    //
    // Event Tracking
    //

    /**
     * @param eventTracker to be registered for tracking application events
     */
    fun addEventTracker(eventTracker: EventTracker) {
        Utils.enforceMainLooper()
        mEventController.addEventTracker(eventTracker)
    }

    /**
     * @param eventTracker to be unregistered from tracking application events
     */
    fun removeEventTracker(eventTracker: EventTracker) {
        Utils.enforceMainLooper()
        mEventController.removeEventTracker(eventTracker)
    }

    /**
     * Tracks an event. Events have a category, action and label. This method can be used to track
     * events such as button presses or other user interactions with your application.
     *
     * @param category resource id of event category
     * @param action resource id of event action
     * @param label resource id of event label
     */
    fun sendEvent(@StringRes category: Int, @StringRes action: Int, @StringRes label: Int) {
        mEventController.sendEvent(category, action, label)
    }

    //
    // Voice Interaction
    //

    fun notifyVoiceSuccess(activity: Activity, message: String) {
        mVoiceController.notifyVoiceSuccess(activity, message)
    }

    fun notifyVoiceFailure(activity: Activity, message: String) {
        mVoiceController.notifyVoiceFailure(activity, message)
    }

    //
    // Shortcuts
    //

    fun updateShortcuts() {
        Utils.enforceMainLooper()
        mShortcutController?.updateShortcuts()
    }

    companion object {
        private val sController = Controller()

        @JvmStatic
        fun getController() = sController
    }
}