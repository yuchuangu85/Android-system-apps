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
package com.android.car.audio;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.content.pm.PackageManager;
import android.hardware.automotive.audiocontrol.V1_0.ContextNumber;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.Bundle;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


public class CarAudioFocus extends AudioPolicy.AudioPolicyFocusListener {

    private static final String TAG = "CarAudioFocus";

    private final AudioManager mAudioManager;
    private final PackageManager mPackageManager;
    private CarAudioService mCarAudioService; // Dynamically assigned just after construction
    private AudioPolicy mAudioPolicy; // Dynamically assigned just after construction


    // Values for the internal interaction matrix we use to make focus decisions
    static final int INTERACTION_REJECT     = 0;    // Focus not granted
    static final int INTERACTION_EXCLUSIVE  = 1;    // Focus granted, others loose focus
    static final int INTERACTION_CONCURRENT = 2;    // Focus granted, others keep focus


    // TODO:  Make this an overlayable resource...
    //  MUSIC           = 1,        // Music playback
    //  NAVIGATION      = 2,        // Navigation directions
    //  VOICE_COMMAND   = 3,        // Voice command session
    //  CALL_RING       = 4,        // Voice call ringing
    //  CALL            = 5,        // Voice call
    //  ALARM           = 6,        // Alarm sound from Android
    //  NOTIFICATION    = 7,        // Notifications
    //  SYSTEM_SOUND    = 8,        // User interaction sounds (button clicks, etc)
    private static int sInteractionMatrix[][] = {
        // Row selected by playing sound (labels along the right)
        // Column selected by incoming request (labels along the top)
        // Cell value is one of INTERACTION_REJECT, INTERACTION_EXCLUSIVE, INTERACTION_CONCURRENT
        // Invalid, Music, Nav, Voice, Ring, Call, Alarm, Notification, System
        {  0,       0,     0,   0,     0,    0,    0,     0,            0 }, // Invalid
        {  0,       1,     2,   1,     1,    1,    1,     2,            2 }, // Music
        {  0,       2,     2,   1,     2,    1,    2,     2,            2 }, // Nav
        {  0,       2,     0,   2,     1,    1,    0,     0,            0 }, // Voice
        {  0,       0,     2,   2,     2,    2,    0,     0,            2 }, // Ring
        {  0,       0,     2,   0,     2,    2,    2,     2,            0 }, // Context
        {  0,       2,     2,   1,     1,    1,    2,     2,            2 }, // Alarm
        {  0,       2,     2,   1,     1,    1,    2,     2,            2 }, // Notification
        {  0,       2,     2,   1,     1,    1,    2,     2,            2 }, // System
    };


    private class FocusEntry {
        // Requester info
        final AudioFocusInfo mAfi;                      // never null

        final int mAudioContext;                        // Which HAL level context does this affect
        final ArrayList<FocusEntry> mBlockers;          // List of requests that block ours
        boolean mReceivedLossTransientCanDuck;          // Whether holder has lost focus duckably

        FocusEntry(AudioFocusInfo afi,
                   int context) {
            mAfi             = afi;
            mAudioContext    = context;
            mBlockers        = new ArrayList<FocusEntry>();
        }

        public String getClientId() {
            return mAfi.getClientId();
        }

        public boolean wantsPauseInsteadOfDucking() {
            return (mAfi.getFlags() & AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS) != 0;
        }

        public boolean receivesDuckEvents() {
            Bundle bundle = mAfi.getAttributes().getBundle();

            if (bundle == null) {
                return false;
            }

            if (!bundle.getBoolean(CarAudioManager.AUDIOFOCUS_EXTRA_RECEIVE_DUCKING_EVENTS)) {
                return false;
            }

            return (mPackageManager.checkPermission(
                            Car.PERMISSION_RECEIVE_CAR_AUDIO_DUCKING_EVENTS,
                            mAfi.getPackageName())
                    == PackageManager.PERMISSION_GRANTED);
        }
    }


    // We keep track of all the focus requesters in this map, with their clientId as the key.
    // This is used both for focus dispatch and death handling
    // Note that the clientId reflects the AudioManager instance and listener object (if any)
    // so that one app can have more than one unique clientId by setting up distinct listeners.
    // Because the listener gets only LOSS/GAIN messages, this is important for an app to do if
    // it expects to request focus concurrently for different USAGEs so it knows which USAGE
    // gained or lost focus at any given moment.  If the SAME listener is used for requests of
    // different USAGE while the earlier request is still in the focus stack (whether holding
    // focus or pending), the new request will be REJECTED so as to avoid any confusion about
    // the meaning of subsequent GAIN/LOSS events (which would continue to apply to the focus
    // request that was already active or pending).
    private final HashMap<String, FocusEntry> mFocusHolders = new HashMap<>();
    private final HashMap<String, FocusEntry> mFocusLosers = new HashMap<>();


    CarAudioFocus(AudioManager audioManager, PackageManager packageManager) {
        mAudioManager = audioManager;
        mPackageManager = packageManager;
    }


    // This has to happen after the construction to avoid a chicken and egg problem when setting up
    // the AudioPolicy which must depend on this object.
    public void setOwningPolicy(CarAudioService audioService, AudioPolicy parentPolicy) {
        mCarAudioService = audioService;
        mAudioPolicy     = parentPolicy;
    }


    // This sends a focus loss message to the targeted requester.
    private void sendFocusLoss(FocusEntry loser, int lossType) {
        Log.i(TAG, "sendFocusLoss (" + focusEventToString(lossType) + ") to "
                + loser.getClientId());
        int result = mAudioManager.dispatchAudioFocusChange(loser.mAfi, lossType, mAudioPolicy);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // TODO:  Is this actually an error, or is it okay for an entry in the focus stack
            // to NOT have a listener?  If that's the case, should we even keep it in the focus
            // stack?
            Log.e(TAG, "Failure to signal loss of audio focus with error: " + result);
        }
    }


    /** @see AudioManager#requestAudioFocus(AudioManager.OnAudioFocusChangeListener, int, int, int) */
    // Note that we replicate most, but not all of the behaviors of the default MediaFocusControl
    // engine as of Android P.
    // Besides the interaction matrix which allows concurrent focus for multiple requestors, which
    // is the reason for this module, we also treat repeated requests from the same clientId
    // slightly differently.
    // If a focus request for the same listener (clientId) is received while that listener is
    // already in the focus stack, we REJECT it outright unless it is for the same USAGE.
    // If it is for the same USAGE, we replace the old request with the new one.
    // The default audio framework's behavior is to remove the previous entry in the stack (no-op
    // if the requester is already holding focus).
    int evaluateFocusRequest(AudioFocusInfo afi) {
        Log.i(TAG, "Evaluating " + focusEventToString(afi.getGainRequest()) + " request for client "
                + afi.getClientId());

        // Is this a request for premanant focus?
        // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -- Means Notifications should be denied
        // AUDIOFOCUS_GAIN_TRANSIENT -- Means current focus holders should get transient loss
        // AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -- Means other can duck (no loss message from us)
        // NOTE:  We expect that in practice it will be permanent for all media requests and
        //        transient for everything else, but that isn't currently an enforced requirement.
        final boolean permanent =
                (afi.getGainRequest() == AudioManager.AUDIOFOCUS_GAIN);
        final boolean allowDucking =
                (afi.getGainRequest() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);


        // Convert from audio attributes "usage" to HAL level "context"
        final int requestedContext = mCarAudioService.getContextForUsage(
                afi.getAttributes().getUsage());

        // If we happen to find entries that this new request should replace, we'll store them here.
        // This happens when a client makes a second AF request on the same listener.
        // After we've granted audio focus to our current request, we'll abandon these requests.
        FocusEntry replacedCurrentEntry = null;
        FocusEntry replacedBlockedEntry = null;

        // Scan all active and pending focus requests.  If any should cause rejection of
        // this new request, then we're done.  Keep a list of those against whom we're exclusive
        // so we can update the relationships if/when we are sure we won't get rejected.
        Log.i(TAG, "Scanning focus holders...");
        final ArrayList<FocusEntry> losers = new ArrayList<FocusEntry>();
        for (FocusEntry entry : mFocusHolders.values()) {
            Log.d(TAG, "Evaluating focus holder: " + entry.getClientId());

            // If this request is for Notifications and a current focus holder has specified
            // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, then reject the request.
            // This matches the hardwired behavior in the default audio policy engine which apps
            // might expect (The interaction matrix doesn't have any provision for dealing with
            // override flags like this).
            if ((requestedContext == ContextNumber.NOTIFICATION) &&
                    (entry.mAfi.getGainRequest() ==
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)) {
                return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }

            // We don't allow sharing listeners (client IDs) between two concurrent requests
            // (because the app would have no way to know to which request a later event applied)
            if (afi.getClientId().equals(entry.mAfi.getClientId())) {
                if (entry.mAudioContext == requestedContext) {
                    // This is a request from a current focus holder.
                    // Abandon the previous request (without sending a LOSS notification to it),
                    // and don't check the interaction matrix for it.
                    Log.i(TAG, "Replacing accepted request from same client");
                    replacedCurrentEntry = entry;
                    continue;
                } else {
                    // Trivially reject a request for a different USAGE
                    Log.e(TAG, "Client " + entry.getClientId() + " has already requested focus "
                            + "for " + entry.mAfi.getAttributes().usageToString() + " - cannot "
                            + "request focus for " + afi.getAttributes().usageToString() + " on "
                            + "same listener.");
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                }
            }

            // Check the interaction matrix for the relationship between this entry and the request
            switch (sInteractionMatrix[entry.mAudioContext][requestedContext]) {
                case INTERACTION_REJECT:
                    // This request is rejected, so nothing further to do
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                case INTERACTION_EXCLUSIVE:
                    // The new request will cause this existing entry to lose focus
                    losers.add(entry);
                    break;
                case INTERACTION_CONCURRENT:
                    // If ducking isn't allowed by the focus requestor, then everybody else
                    // must get a LOSS.
                    // If a focus holder has set the AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS flag,
                    // they must get a LOSS message even if ducking would otherwise be allowed.
                    // If a focus holder holds the RECEIVE_CAR_AUDIO_DUCKING_EVENTS permission,
                    // they must receive all audio focus losses.
                    if (!allowDucking
                            || entry.wantsPauseInsteadOfDucking()
                            || entry.receivesDuckEvents()) {
                        losers.add(entry);
                    }
                    break;
                default:
                    Log.e(TAG, "Bad interaction matrix value - rejecting");
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }
        }
        Log.i(TAG, "Scanning those who've already lost focus...");
        final ArrayList<FocusEntry> blocked = new ArrayList<FocusEntry>();
        for (FocusEntry entry : mFocusLosers.values()) {
            Log.i(TAG, entry.mAfi.getClientId());

            // If this request is for Notifications and a pending focus holder has specified
            // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, then reject the request
            if ((requestedContext == ContextNumber.NOTIFICATION) &&
                    (entry.mAfi.getGainRequest() ==
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)) {
                return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }

            // We don't allow sharing listeners (client IDs) between two concurrent requests
            // (because the app would have no way to know to which request a later event applied)
            if (afi.getClientId().equals(entry.mAfi.getClientId())) {
                if (entry.mAudioContext == requestedContext) {
                    // This is a repeat of a request that is currently blocked.
                    // Evaluate it as if it were a new request, but note that we should remove
                    // the old pending request, and move it.
                    // We do not want to evaluate the new request against itself.
                    Log.i(TAG, "Replacing pending request from same client");
                    replacedBlockedEntry = entry;
                    continue;
                } else {
                    // Trivially reject a request for a different USAGE
                    Log.e(TAG, "Client " + entry.getClientId() + " has already requested focus "
                            + "for " + entry.mAfi.getAttributes().usageToString() + " - cannot "
                            + "request focus for " + afi.getAttributes().usageToString() + " on "
                            + "same listener.");
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                }
            }

            // Check the interaction matrix for the relationship between this entry and the request
            switch (sInteractionMatrix[entry.mAudioContext][requestedContext]) {
                case INTERACTION_REJECT:
                    // Even though this entry has currently lost focus, the fact that it is
                    // waiting to play means we'll reject this new conflicting request.
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                case INTERACTION_EXCLUSIVE:
                    // The new request is yet another reason this entry cannot regain focus (yet)
                    blocked.add(entry);
                    break;
                case INTERACTION_CONCURRENT:
                    // If ducking is not allowed by the requester, or the pending focus holder had
                    // set the AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS flag, or if the pending
                    // focus holder has requested to receive all focus events, then the pending
                    // holder must stay "lost" until this requester goes away.
                    if (!allowDucking
                            || entry.wantsPauseInsteadOfDucking()
                            || entry.receivesDuckEvents()) {
                        // The new request is yet another reason this entry cannot regain focus yet
                        blocked.add(entry);
                    }
                    break;
                default:
                    Log.e(TAG, "Bad interaction matrix value - rejecting");
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }
        }


        // Now that we've decided we'll grant focus, construct our new FocusEntry
        FocusEntry newEntry = new FocusEntry(afi, requestedContext);

        // These entries have permanently lost focus as a result of this request, so they
        // should be removed from all blocker lists.
        ArrayList<FocusEntry> permanentlyLost = new ArrayList<>();

        if (replacedCurrentEntry != null) {
            mFocusHolders.remove(replacedCurrentEntry.getClientId());
            permanentlyLost.add(replacedCurrentEntry);
        }
        if (replacedBlockedEntry != null) {
            mFocusLosers.remove(replacedBlockedEntry.getClientId());
            permanentlyLost.add(replacedBlockedEntry);
        }


        // Now that we're sure we'll accept this request, update any requests which we would
        // block but are already out of focus but waiting to come back
        for (FocusEntry entry : blocked) {
            // If we're out of focus it must be because somebody is blocking us
            assert !entry.mBlockers.isEmpty();

            if (permanent) {
                // This entry has now lost focus forever
                sendFocusLoss(entry, AudioManager.AUDIOFOCUS_LOSS);
                entry.mReceivedLossTransientCanDuck = false;
                final FocusEntry deadEntry = mFocusLosers.remove(entry.mAfi.getClientId());
                assert deadEntry != null;
                permanentlyLost.add(entry);
            } else {
                if (!allowDucking && entry.mReceivedLossTransientCanDuck) {
                    // This entry was previously allowed to duck, but can no longer do so.
                    Log.i(TAG, "Converting duckable loss to non-duckable for "
                            + entry.getClientId());
                    sendFocusLoss(entry, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
                    entry.mReceivedLossTransientCanDuck = false;
                }
                // Note that this new request is yet one more reason we can't (yet) have focus
                entry.mBlockers.add(newEntry);
            }
        }

        // Notify and update any requests which are now losing focus as a result of the new request
        for (FocusEntry entry : losers) {
            // If we have focus (but are about to loose it), nobody should be blocking us yet
            assert entry.mBlockers.isEmpty();

            int lossType;
            if (permanent) {
                lossType = AudioManager.AUDIOFOCUS_LOSS;
            } else if (allowDucking && entry.receivesDuckEvents()) {
                lossType = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                entry.mReceivedLossTransientCanDuck = true;
            } else {
                lossType = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
            }
            sendFocusLoss(entry, lossType);

            // The entry no longer holds focus, so take it out of the holders list
            mFocusHolders.remove(entry.mAfi.getClientId());

            if (permanent) {
                permanentlyLost.add(entry);
            } else {
                // Add ourselves to the list of requests waiting to get focus back and
                // note why we lost focus so we can tell when it's time to get it back
                mFocusLosers.put(entry.mAfi.getClientId(), entry);
                entry.mBlockers.add(newEntry);
            }
        }

        // Now that all new blockers have been added, clear out any other requests that have been
        // permanently lost as a result of this request. Treat them as abandoned - if they're on
        // any blocker lists, remove them. If any focus requests become unblocked as a result,
        // re-grant them. (This can happen when a GAIN_TRANSIENT_MAY_DUCK request replaces a
        // GAIN_TRANSIENT request from the same listener.)
        for (FocusEntry entry : permanentlyLost) {
            Log.d(TAG, "Cleaning up entry " + entry.getClientId());
            removeFocusEntryAndRestoreUnblockedWaiters(entry);
        }

        // Finally, add the request we're granting to the focus holders' list
        mFocusHolders.put(afi.getClientId(), newEntry);

        Log.i(TAG, "AUDIOFOCUS_REQUEST_GRANTED");
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }


    @Override
    public synchronized void onAudioFocusRequest(AudioFocusInfo afi, int requestResult) {
        Log.i(TAG, "onAudioFocusRequest " + afi.getClientId());

        int response = evaluateFocusRequest(afi);

        // Post our reply for delivery to the original focus requester
        mAudioManager.setFocusRequestResult(afi, response, mAudioPolicy);
    }


    /**
     * @see AudioManager#abandonAudioFocus(AudioManager.OnAudioFocusChangeListener, AudioAttributes)
     * Note that we'll get this call for a focus holder that dies while in the focus stack, so
     * we don't need to watch for death notifications directly.
     * */
    @Override
    public synchronized void onAudioFocusAbandon(AudioFocusInfo afi) {
        Log.i(TAG, "onAudioFocusAbandon " + afi.getClientId());

        FocusEntry deadEntry = removeFocusEntry(afi);

        if (deadEntry != null) {
            removeFocusEntryAndRestoreUnblockedWaiters(deadEntry);
        }
    }

    /**
     * Remove Focus entry from focus holder or losers entry lists
     * @param afi Audio Focus Info to remove
     * @return Removed Focus Entry
     */
    private FocusEntry removeFocusEntry(AudioFocusInfo afi) {
        Log.i(TAG, "removeFocusEntry " + afi.getClientId());

        // Remove this entry from our active or pending list
        FocusEntry deadEntry = mFocusHolders.remove(afi.getClientId());
        if (deadEntry == null) {
            deadEntry = mFocusLosers.remove(afi.getClientId());
            if (deadEntry == null) {
                // Caller is providing an unrecognzied clientId!?
                Log.w(TAG, "Audio focus abandoned by unrecognized client id: " + afi.getClientId());
                // This probably means an app double released focused for some reason.  One
                // harmless possibility is a race between an app being told it lost focus and the
                // app voluntarily abandoning focus.  More likely the app is just sloppy.  :)
                // The more nefarious possibility is that the clientId is actually corrupted
                // somehow, in which case we might have a real focus entry that we're going to fail
                // to remove. If that were to happen, I'd expect either the app to swallow it
                // silently, or else take unexpected action (eg: resume playing spontaneously), or
                // else to see "Failure to signal ..." gain/loss error messages in the log from
                // this module when a focus change tries to take action on a truly zombie entry.
            }
        }
        return deadEntry;
    }

    private void removeFocusEntryAndRestoreUnblockedWaiters(FocusEntry deadEntry) {
        // Remove this entry from the blocking list of any pending requests
        Iterator<FocusEntry> it = mFocusLosers.values().iterator();
        while (it.hasNext()) {
            FocusEntry entry = it.next();

            // Remove the retiring entry from all blocker lists
            entry.mBlockers.remove(deadEntry);

            // Any entry whose blocking list becomes empty should regain focus
            if (entry.mBlockers.isEmpty()) {
                Log.i(TAG, "Restoring unblocked entry " + entry.getClientId());
                // Pull this entry out of the focus losers list
                it.remove();

                // Add it back into the focus holders list
                mFocusHolders.put(entry.getClientId(), entry);

                dispatchFocusGained(entry.mAfi);

            }
        }
    }

    /**
     * Dispatch focus gain
     * @param afi Audio focus info
     * @return AudioManager.AUDIOFOCUS_REQUEST_GRANTED if focus is dispatched successfully
     */
    private int dispatchFocusGained(AudioFocusInfo afi) {
        // Send the focus (re)gain notification
        int result = mAudioManager.dispatchAudioFocusChange(
                afi,
                afi.getGainRequest(),
                mAudioPolicy);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // TODO:  Is this actually an error, or is it okay for an entry in the focus
            // stack to NOT have a listener?  If that's the case, should we even keep
            // it in the focus stack?
            Log.e(TAG, "Failure to signal gain of audio focus with error: " + result);
        }
        return result;
    }


    /**
     * Query the current list of focus loser for uid
     * @param uid uid to query current focus loser
     * @return list of current focus losers for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusLosersForUid(int uid) {
        return getAudioFocusListForUid(uid, mFocusLosers);
    }

    /**
     * Query the current list of focus holders for uid
     * @param uid uid to query current focus holders
     * @return list of current focus holders that for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusHoldersForUid(int uid) {
        return getAudioFocusListForUid(uid, mFocusHolders);
    }

    /**
     * Query input list for matching uid
     * @param uid uid to match in map
     * @param mapToQuery map to query for uid info
     * @return list of audio focus info that match uid
     */
    private ArrayList<AudioFocusInfo> getAudioFocusListForUid(int uid,
            HashMap<String, FocusEntry> mapToQuery) {
        ArrayList<AudioFocusInfo> matchingInfoList = new ArrayList<>();
        for (String clientId : mapToQuery.keySet()) {
            AudioFocusInfo afi = mapToQuery.get(clientId).mAfi;
            if (afi.getClientUid() == uid) {
                matchingInfoList.add(afi);
            }
        }
        return matchingInfoList;
    }

    /**
     * Remove the audio focus info, if entry is still active
     * dispatch lose focus transient to listeners
     * @param afi Audio Focus info to remove
     */
    void removeAudioFocusInfoAndTransientlyLoseFocus(AudioFocusInfo afi) {
        FocusEntry deadEntry = removeFocusEntry(afi);

        if (deadEntry != null) {
            sendFocusLoss(deadEntry, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            removeFocusEntryAndRestoreUnblockedWaiters(deadEntry);
        }
    }

    /**
     * Reevaluate focus request and regain focus
     * @param afi audio focus info to reevaluate
     * @return AudioManager.AUDIOFOCUS_REQUEST_GRANTED if focus is granted
     */
    int reevaluateAndRegainAudioFocus(AudioFocusInfo afi) {
        int results = evaluateFocusRequest(afi);

        if (results == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return dispatchFocusGained(afi);
        }
        return results;
    }

    /**
     * dumps the current state of the CarAudioFocus object
     * @param indent indent to add to each line in the current stream
     * @param writer stream to write to
     */
    public synchronized void dump(String indent, PrintWriter writer) {
        writer.printf("%s*CarAudioFocus*\n", indent);

        writer.printf("%s\tCurrent Focus Holders:\n", indent);
        for (String clientId : mFocusHolders.keySet()) {
            writer.printf("%s\t\t%s\n", indent, clientId);
        }

        writer.printf("%s\tTransient Focus Losers:\n", indent);
        for (String clientId : mFocusLosers.keySet()) {
            writer.printf("%s\t\t%s\n", indent, clientId);
        }
    }

    private static String focusEventToString(int focusEvent) {
        switch (focusEvent) {
            case AudioManager.AUDIOFOCUS_GAIN:
                return "GAIN";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                return "GAIN_TRANSIENT";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                return "GAIN_TRANSIENT_EXCLUSIVE";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                return "GAIN_TRANSIENT_MAY_DUCK";
            case AudioManager.AUDIOFOCUS_LOSS:
                return "LOSS";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                return "LOSS_TRANSIENT";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                return "LOSS_TRANSIENT_CAN_DUCK";
            default:
                return "unknown event " + focusEvent;
        }
    }
}
