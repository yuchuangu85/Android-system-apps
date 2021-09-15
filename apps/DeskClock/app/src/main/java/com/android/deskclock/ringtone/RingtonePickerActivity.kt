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

package com.android.deskclock.ringtone

import android.app.Dialog
import android.content.Context
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.deskclock.BaseActivity
import com.android.deskclock.DropShadowController
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.RingtonePreviewKlaxon
import com.android.deskclock.ItemAdapter
import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.ItemAdapter.OnItemClickedListener
import com.android.deskclock.actionbarmenu.MenuItemControllerFactory
import com.android.deskclock.actionbarmenu.NavUpMenuItemController
import com.android.deskclock.actionbarmenu.OptionsMenuManager
import com.android.deskclock.alarms.AlarmUpdateHandler
import com.android.deskclock.data.DataModel
import com.android.deskclock.provider.Alarm

/**
 * This activity presents a set of ringtones from which the user may select one. The set includes:
 *
 *  * system ringtones from the Android framework
 *  * a ringtone representing pure silence
 *  * a ringtone representing a default ringtone
 *  * user-selected audio files available as ringtones
 *
 */
// TODO(b/165664115) Replace deprecated AsyncTask calls
class RingtonePickerActivity : BaseActivity(), LoaderCallbacks<List<ItemHolder<Uri?>>> {
    /** The controller that shows the drop shadow when content is not scrolled to the top.  */
    private var mDropShadowController: DropShadowController? = null

    /** Generates the items in the activity context menu.  */
    private lateinit var mOptionsMenuManager: OptionsMenuManager

    /** Displays a set of selectable ringtones.  */
    private lateinit var mRecyclerView: RecyclerView

    /** Stores the set of ItemHolders that wrap the selectable ringtones.  */
    private lateinit var mRingtoneAdapter: ItemAdapter<ItemHolder<Uri?>>

    /** The title of the default ringtone.  */
    private var mDefaultRingtoneTitle: String? = null

    /** The uri of the default ringtone.  */
    private var mDefaultRingtoneUri: Uri? = null

    /** The uri of the ringtone to select after data is loaded.  */
    private var mSelectedRingtoneUri: Uri? = null

    /** `true` indicates the [.mSelectedRingtoneUri] must be played after data load.  */
    private var mIsPlaying = false

    /** Identifies the alarm to receive the selected ringtone; -1 indicates there is no alarm.  */
    private var mAlarmId: Long = -1

    /** The location of the custom ringtone to be removed.  */
    private var mIndexOfRingtoneToRemove: Int = RecyclerView.NO_POSITION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ringtone_picker)
        setVolumeControlStream(AudioManager.STREAM_ALARM)

        mOptionsMenuManager = OptionsMenuManager()
        mOptionsMenuManager.addMenuItemController(NavUpMenuItemController(this))
                .addMenuItemController(*MenuItemControllerFactory.buildMenuItemControllers(this))

        val context: Context = getApplicationContext()
        val intent: Intent = getIntent()

        if (savedInstanceState != null) {
            mIsPlaying = savedInstanceState.getBoolean(STATE_KEY_PLAYING)
            mSelectedRingtoneUri = savedInstanceState.getParcelable(EXTRA_RINGTONE_URI)
        }

        if (mSelectedRingtoneUri == null) {
            mSelectedRingtoneUri = intent.getParcelableExtra(EXTRA_RINGTONE_URI)
        }

        mAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        mDefaultRingtoneUri = intent.getParcelableExtra(EXTRA_DEFAULT_RINGTONE_URI)
        val defaultRingtoneTitleId = intent.getIntExtra(EXTRA_DEFAULT_RINGTONE_NAME, 0)
        mDefaultRingtoneTitle = context.getString(defaultRingtoneTitleId)

        val inflater: LayoutInflater = getLayoutInflater()
        val listener: OnItemClickedListener = ItemClickWatcher()
        val ringtoneFactory: ItemViewHolder.Factory = RingtoneViewHolder.Factory(inflater)
        val headerFactory: ItemViewHolder.Factory = HeaderViewHolder.Factory(inflater)
        val addNewFactory: ItemViewHolder.Factory = AddCustomRingtoneViewHolder.Factory(inflater)
        mRingtoneAdapter = ItemAdapter()
        mRingtoneAdapter
                .withViewTypes(headerFactory, null, HeaderViewHolder.VIEW_TYPE_ITEM_HEADER)
                .withViewTypes(addNewFactory, listener,
                        AddCustomRingtoneViewHolder.VIEW_TYPE_ADD_NEW)
                .withViewTypes(ringtoneFactory, listener, RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND)
                .withViewTypes(ringtoneFactory, listener, RingtoneViewHolder.VIEW_TYPE_CUSTOM_SOUND)

        mRecyclerView = findViewById(R.id.ringtone_content) as RecyclerView
        mRecyclerView.setLayoutManager(LinearLayoutManager(context))
        mRecyclerView.setAdapter(mRingtoneAdapter)
        mRecyclerView.setItemAnimator(null)

        mRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (mIndexOfRingtoneToRemove != RecyclerView.NO_POSITION) {
                    closeContextMenu()
                }
            }
        })

        val titleResourceId = intent.getIntExtra(EXTRA_TITLE, 0)
        setTitle(context.getString(titleResourceId))

        LoaderManager.getInstance(this).initLoader(0 /* id */, Bundle.EMPTY /* args */,
                this /* callback */)

        registerForContextMenu(mRecyclerView)
    }

    override fun onResume() {
        super.onResume()

        val dropShadow: View = findViewById(R.id.drop_shadow)
        mDropShadowController = DropShadowController(dropShadow, mRecyclerView)
    }

    override fun onPause() {
        mDropShadowController!!.stop()
        mDropShadowController = null

        mSelectedRingtoneUri?.let {
            if (mAlarmId != -1L) {
                val context: Context = getApplicationContext()
                val cr: ContentResolver = getContentResolver()

                // Start a background task to fetch the alarm whose ringtone must be updated.
                object : AsyncTask<Void?, Void?, Alarm>() {
                    override fun doInBackground(vararg parameters: Void?): Alarm? {
                        val alarm = Alarm.getAlarm(cr, mAlarmId)
                        if (alarm != null) {
                            alarm.alert = it
                        }
                        return alarm
                    }

                    override fun onPostExecute(alarm: Alarm) {
                        // Update the default ringtone for future new alarms.
                        DataModel.dataModel.defaultAlarmRingtoneUri = alarm.alert!!

                        // Start a second background task to persist the updated alarm.
                        AlarmUpdateHandler(context, mScrollHandler = null, mSnackbarAnchor = null)
                                .asyncUpdateAlarm(alarm, popToast = false, minorUpdate = true)
                    }
                }.execute()
            } else {
                DataModel.dataModel.timerRingtoneUri = it
            }
        }

        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations()) {
            stopPlayingRingtone(selectedRingtoneHolder, false)
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_KEY_PLAYING, mIsPlaying)
        outState.putParcelable(EXTRA_RINGTONE_URI, mSelectedRingtoneUri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mOptionsMenuManager.onCreateOptionsMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mOptionsMenuManager.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mOptionsMenuManager.onOptionsItemSelected(item) ||
                super.onOptionsItemSelected(item)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<ItemHolder<Uri?>>> {
        return RingtoneLoader(getApplicationContext(), mDefaultRingtoneUri!!,
                mDefaultRingtoneTitle!!)
    }

    override fun onLoadFinished(
        loader: Loader<List<ItemHolder<Uri?>>>,
        itemHolders: List<ItemHolder<Uri?>>
    ) {
        // Update the adapter with fresh data.
        mRingtoneAdapter.setItems(itemHolders)

        // Attempt to select the requested ringtone.
        val toSelect = getRingtoneHolder(mSelectedRingtoneUri)
        if (toSelect != null) {
            toSelect.isSelected = true
            mSelectedRingtoneUri = toSelect.uri
            toSelect.notifyItemChanged()

            // Start playing the ringtone if indicated.
            if (mIsPlaying) {
                startPlayingRingtone(toSelect)
            }
        } else {
            // Clear the selection since it does not exist in the data.
            RingtonePreviewKlaxon.stop(this)
            mSelectedRingtoneUri = null
            mIsPlaying = false
        }
    }

    override fun onLoaderReset(loader: Loader<List<ItemHolder<Uri?>>>) {
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data ?: return

        // Bail if the permission to read (playback) the audio at the uri was not granted.
        val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (flags != Intent.FLAG_GRANT_READ_URI_PERMISSION) {
            return
        }

        // Start a task to fetch the display name of the audio content and add the custom ringtone.
        AddCustomRingtoneTask(uri).execute()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // Find the ringtone to be removed.
        val items = mRingtoneAdapter.items
        val toRemove = items!![mIndexOfRingtoneToRemove] as RingtoneHolder
        mIndexOfRingtoneToRemove = RecyclerView.NO_POSITION

        // Launch the confirmation dialog.
        val manager: FragmentManager = supportFragmentManager
        val hasPermissions = toRemove.hasPermissions()
        ConfirmRemoveCustomRingtoneDialogFragment.show(manager, toRemove.uri, hasPermissions)
        return true
    }

    private fun getRingtoneHolder(uri: Uri?): RingtoneHolder? {
        for (itemHolder in mRingtoneAdapter.items!!) {
            if (itemHolder is RingtoneHolder) {
                if (itemHolder.uri == uri) {
                    return itemHolder
                }
            }
        }

        return null
    }

    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val selectedRingtoneHolder: RingtoneHolder?
        get() = getRingtoneHolder(mSelectedRingtoneUri)

    /**
     * The given `ringtone` will be selected as a side-effect of playing the ringtone.
     *
     * @param ringtone the ringtone to be played
     */
    private fun startPlayingRingtone(ringtone: RingtoneHolder) {
        if (!ringtone.isPlaying && !ringtone.isSilent) {
            RingtonePreviewKlaxon.start(getApplicationContext(), ringtone.uri)
            ringtone.isPlaying = true
            mIsPlaying = true
        }
        if (!ringtone.isSelected) {
            ringtone.isSelected = true
            mSelectedRingtoneUri = ringtone.uri
        }
        ringtone.notifyItemChanged()
    }

    /**
     * @param ringtone the ringtone to stop playing
     * @param deselect `true` indicates the ringtone should also be deselected;
     * `false` indicates its selection state should remain unchanged
     */
    private fun stopPlayingRingtone(ringtone: RingtoneHolder?, deselect: Boolean) {
        if (ringtone == null) {
            return
        }

        if (ringtone.isPlaying) {
            RingtonePreviewKlaxon.stop(this)
            ringtone.isPlaying = false
            mIsPlaying = false
        }
        if (deselect && ringtone.isSelected) {
            ringtone.isSelected = false
            mSelectedRingtoneUri = null
        }
        ringtone.notifyItemChanged()
    }

    /**
     * Proceeds with removing the custom ringtone with the given uri.
     *
     * @param toRemove identifies the custom ringtone to be removed
     */
    private fun removeCustomRingtone(toRemove: Uri) {
        RemoveCustomRingtoneTask(toRemove).execute()
    }

    /**
     * This DialogFragment informs the user of the side-effects of removing a custom ringtone while
     * it is in use by alarms and/or timers and prompts them to confirm the removal.
     */
    class ConfirmRemoveCustomRingtoneDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val arguments = requireArguments()
            val toRemove = arguments.getParcelable<Uri>(ARG_RINGTONE_URI_TO_REMOVE)

            val okListener = DialogInterface.OnClickListener { _, _ ->
                (activity as RingtonePickerActivity).removeCustomRingtone(toRemove!!)
            }

            return if (arguments.getBoolean(ARG_RINGTONE_HAS_PERMISSIONS)) {
                AlertDialog.Builder(requireActivity())
                        .setPositiveButton(R.string.remove_sound, okListener)
                        .setNegativeButton(android.R.string.cancel, null /* listener */)
                        .setMessage(R.string.confirm_remove_custom_ringtone)
                        .create()
            } else {
                AlertDialog.Builder(requireActivity())
                        .setPositiveButton(R.string.remove_sound, okListener)
                        .setMessage(R.string.custom_ringtone_lost_permissions)
                        .create()
            }
        }

        companion object {
            private const val ARG_RINGTONE_URI_TO_REMOVE = "arg_ringtone_uri_to_remove"
            private const val ARG_RINGTONE_HAS_PERMISSIONS = "arg_ringtone_has_permissions"

            fun show(manager: FragmentManager, toRemove: Uri?, hasPermissions: Boolean) {
                if (manager.isDestroyed) {
                    return
                }

                val args = Bundle()
                args.putParcelable(ARG_RINGTONE_URI_TO_REMOVE, toRemove)
                args.putBoolean(ARG_RINGTONE_HAS_PERMISSIONS, hasPermissions)

                val fragment: DialogFragment = ConfirmRemoveCustomRingtoneDialogFragment()
                fragment.arguments = args
                fragment.isCancelable = hasPermissions
                fragment.show(manager, "confirm_ringtone_remove")
            }
        }
    }

    /**
     * This click handler alters selection and playback of ringtones. It also launches the system
     * file chooser to search for openable audio files that may serve as ringtones.
     */
    private inner class ItemClickWatcher : OnItemClickedListener {
        override fun onItemClicked(viewHolder: ItemViewHolder<*>, id: Int) {
            when (id) {
                AddCustomRingtoneViewHolder.CLICK_ADD_NEW -> {
                    stopPlayingRingtone(selectedRingtoneHolder, false)
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("audio/*"), 0)
                }
                RingtoneViewHolder.CLICK_NORMAL -> {
                    val oldSelection = selectedRingtoneHolder
                    val newSelection = viewHolder.itemHolder as RingtoneHolder

                    // Tapping the existing selection toggles playback of the ringtone.
                    if (oldSelection === newSelection) {
                        if (newSelection.isPlaying) {
                            stopPlayingRingtone(newSelection, false)
                        } else {
                            startPlayingRingtone(newSelection)
                        }
                    } else {
                        // Tapping a new selection changes the selection and playback.
                        stopPlayingRingtone(oldSelection, true)
                        startPlayingRingtone(newSelection)
                    }
                }
                RingtoneViewHolder.CLICK_LONG_PRESS -> {
                    mIndexOfRingtoneToRemove = viewHolder.getAdapterPosition()
                }
                RingtoneViewHolder.CLICK_NO_PERMISSIONS -> {
                    ConfirmRemoveCustomRingtoneDialogFragment.show(supportFragmentManager,
                            (viewHolder.itemHolder as RingtoneHolder).uri, false)
                }
            }
        }
    }

    /**
     * This task locates a displayable string in the background that is fit for use as the title of
     * the audio content. It adds a custom ringtone using the uri and title on the main thread.
     */
    private inner class AddCustomRingtoneTask(private val mUri: Uri)
        : AsyncTask<Void?, Void?, String>() {
        private val mContext: Context = getApplicationContext()

        override fun doInBackground(vararg voids: Void?): String {
            val contentResolver = mContext.contentResolver

            // Take the long-term permission to read (playback) the audio at the uri.
            contentResolver
                    .takePersistableUriPermission(mUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                contentResolver.query(mUri, null, null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        // If the file was a media file, return its title.
                        val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                        if (titleIndex != -1) {
                            return cursor.getString(titleIndex)
                        }

                        // If the file was a simple openable, return its display name.
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            var title = cursor.getString(displayNameIndex)
                            val dotIndex = title.lastIndexOf(".")
                            if (dotIndex > 0) {
                                title = title.substring(0, dotIndex)
                            }
                            return title
                        }
                    } else {
                        LogUtils.e("No ringtone for uri: %s", mUri)
                    }
                }
            } catch (e: Exception) {
                LogUtils.e("Unable to locate title for custom ringtone: $mUri", e)
            }

            return mContext.getString(R.string.unknown_ringtone_title)
        }

        override fun onPostExecute(title: String) {
            // Add the new custom ringtone to the data model.
            DataModel.dataModel.addCustomRingtone(mUri, title)

            // When the loader completes, it must play the new ringtone.
            mSelectedRingtoneUri = mUri
            mIsPlaying = true

            // Reload the data to reflect the change in the UI.
            LoaderManager.getInstance(this@RingtonePickerActivity).restartLoader(0 /* id */,
                    null /* args */, this@RingtonePickerActivity /* callback */)
        }
    }

    /**
     * Removes a custom ringtone with the given uri. Taking this action has side-effects because
     * all alarms that use the custom ringtone are reassigned to the Android system default alarm
     * ringtone. If the application's default alarm ringtone is being removed, it is reset to the
     * Android system default alarm ringtone. If the application's timer ringtone is being removed,
     * it is reset to the application's default timer ringtone.
     */
    private inner class RemoveCustomRingtoneTask(private val mRemoveUri: Uri)
        : AsyncTask<Void?, Void?, Void?>() {
        private lateinit var mSystemDefaultRingtoneUri: Uri

        override fun doInBackground(vararg voids: Void?): Void? {
            mSystemDefaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            // Update all alarms that use the custom ringtone to use the system default.
            val cr: ContentResolver = getContentResolver()
            val alarms = Alarm.getAlarms(cr, null)
            for (alarm in alarms) {
                if (mRemoveUri == alarm.alert) {
                    alarm.alert = mSystemDefaultRingtoneUri
                    // Start a second background task to persist the updated alarm.
                    AlarmUpdateHandler(this@RingtonePickerActivity, null, null)
                            .asyncUpdateAlarm(alarm, popToast = false, minorUpdate = true)
                }
            }

            try {
                // Release the permission to read (playback) the audio at the uri.
                cr.releasePersistableUriPermission(mRemoveUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignore: SecurityException) {
                // If the file was already deleted from the file system, a SecurityException is
                // thrown indicating this app did not hold the read permission being released.
                LogUtils.w("SecurityException while releasing read permission for $mRemoveUri")
            }

            return null
        }

        override fun onPostExecute(v: Void?) {
            // Reset the default alarm ringtone if it was just removed.
            if (mRemoveUri == DataModel.dataModel.defaultAlarmRingtoneUri) {
                DataModel.dataModel.defaultAlarmRingtoneUri = mSystemDefaultRingtoneUri
            }

            // Reset the timer ringtone if it was just removed.
            if (mRemoveUri == DataModel.dataModel.timerRingtoneUri) {
                val timerRingtoneUri = DataModel.dataModel.defaultTimerRingtoneUri
                DataModel.dataModel.timerRingtoneUri = timerRingtoneUri
            }

            // Remove the corresponding custom ringtone.
            DataModel.dataModel.removeCustomRingtone(mRemoveUri)

            // Find the ringtone to be removed from the adapter.
            val toRemove = getRingtoneHolder(mRemoveUri) ?: return

            // If the ringtone to remove is also the selected ringtone, adjust the selection.
            if (toRemove.isSelected) {
                stopPlayingRingtone(toRemove, false)
                val defaultRingtone = getRingtoneHolder(mDefaultRingtoneUri)
                if (defaultRingtone != null) {
                    defaultRingtone.isSelected = true
                    mSelectedRingtoneUri = defaultRingtone.uri
                    defaultRingtone.notifyItemChanged()
                }
            }

            // Remove the ringtone from the adapter.
            mRingtoneAdapter.removeItem(toRemove)
        }
    }

    companion object {
        /** Key to an extra that defines resource id to the title of this activity.  */
        private const val EXTRA_TITLE = "extra_title"

        /** Key to an extra that identifies the alarm to which the selected ringtone is attached. */
        private const val EXTRA_ALARM_ID = "extra_alarm_id"

        /** Key to an extra that identifies the selected ringtone.  */
        private const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"

        /** Key to an extra that defines the uri representing the default ringtone.  */
        private const val EXTRA_DEFAULT_RINGTONE_URI = "extra_default_ringtone_uri"

        /** Key to an extra that defines the name of the default ringtone.  */
        private const val EXTRA_DEFAULT_RINGTONE_NAME = "extra_default_ringtone_name"

        /** Key to an instance state value indicating if the
         * selected ringtone is currently playing. */
        private const val STATE_KEY_PLAYING = "extra_is_playing"

        /**
         * @return an intent that launches the ringtone picker to edit the ringtone of the given
         * `alarm`
         */
        @JvmStatic
        @Keep
        fun createAlarmRingtonePickerIntent(context: Context, alarm: Alarm): Intent {
            return Intent(context, RingtonePickerActivity::class.java)
                    .putExtra(EXTRA_TITLE, R.string.alarm_sound)
                    .putExtra(EXTRA_ALARM_ID, alarm.id)
                    .putExtra(EXTRA_RINGTONE_URI, alarm.alert)
                    .putExtra(EXTRA_DEFAULT_RINGTONE_URI,
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    .putExtra(EXTRA_DEFAULT_RINGTONE_NAME, R.string.default_alarm_ringtone_title)
        }

        /**
         * @return an intent that launches the ringtone picker to edit the ringtone of all timers
         */
        @JvmStatic
        @Keep
        fun createTimerRingtonePickerIntent(context: Context): Intent {
            val dataModel = DataModel.dataModel
            return Intent(context, RingtonePickerActivity::class.java)
                    .putExtra(EXTRA_TITLE, R.string.timer_sound)
                    .putExtra(EXTRA_RINGTONE_URI, dataModel.timerRingtoneUri)
                    .putExtra(EXTRA_DEFAULT_RINGTONE_URI, dataModel.defaultTimerRingtoneUri)
                    .putExtra(EXTRA_DEFAULT_RINGTONE_NAME, R.string.default_timer_ringtone_title)
        }
    }
}