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

package com.android.car.settings.security;

import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.Logger;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternView.DisplayMode;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fragment for choosing security lock pattern.
 */
public class ChooseLockPatternFragment extends BaseFragment {

    private static final Logger LOG = new Logger(ChooseLockPatternFragment.class);
    private static final String FRAGMENT_TAG_SAVE_PATTERN_WORKER = "save_pattern_worker";
    private static final String STATE_UI_STAGE = "state_ui_stage";
    private static final String STATE_CHOSEN_PATTERN = "state_chosen_pattern";
    private static final int ID_EMPTY_MESSAGE = -1;
    /**
     * The patten used during the help screen to show how to draw a pattern.
     */
    private final List<LockPatternView.Cell> mAnimatePattern =
            Collections.unmodifiableList(Lists.newArrayList(
                    LockPatternView.Cell.of(0, 0),
                    LockPatternView.Cell.of(0, 1),
                    LockPatternView.Cell.of(1, 1),
                    LockPatternView.Cell.of(2, 1)
            ));
    // How long we wait to clear a wrong pattern
    private int mWrongPatternClearTimeOut;
    private int mUserId;
    private Stage mUiStage = Stage.Introduction;
    private LockPatternView mLockPatternView;
    private TextView mMessageText;
    private Button mSecondaryButton;
    private Button mPrimaryButton;
    private ProgressBar mProgressBar;
    private List<LockPatternView.Cell> mChosenPattern;
    // Existing pattern that user previously set
    private byte[] mCurrentPattern;
    private SavePatternWorker mSavePatternWorker;
    private Runnable mClearPatternRunnable = () -> mLockPatternView.clearPattern();
    // The pattern listener that responds according to a user choosing a new
    // lock pattern.
    private final LockPatternView.OnPatternListener mChooseNewLockPatternListener =
            new LockPatternView.OnPatternListener() {
                @Override
                public void onPatternStart() {
                    mLockPatternView.removeCallbacks(mClearPatternRunnable);
                    updateUIWhenPatternInProgress();
                }

                @Override
                public void onPatternCleared() {
                    mLockPatternView.removeCallbacks(mClearPatternRunnable);
                }

                @Override
                public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                    switch (mUiStage) {
                        case Introduction:
                        case ChoiceTooShort:
                            handlePatternEntered(pattern);
                            break;
                        case ConfirmWrong:
                        case NeedToConfirm:
                            handleConfirmPattern(pattern);
                            break;
                        default:
                            throw new IllegalStateException("Unexpected stage " + mUiStage
                                    + " when entering the pattern.");
                    }
                }

                @Override
                public void onPatternCellAdded(List<Cell> pattern) {
                }

                private void handleConfirmPattern(List<LockPatternView.Cell> pattern) {
                    if (mChosenPattern == null) {
                        throw new IllegalStateException(
                                "null chosen pattern in stage 'need to confirm");
                    }
                    if (mChosenPattern.equals(pattern)) {
                        updateStage(Stage.ChoiceConfirmed);
                    } else {
                        updateStage(Stage.ConfirmWrong);
                    }
                }

                private void handlePatternEntered(List<LockPatternView.Cell> pattern) {
                    if (pattern.size() < LockPatternUtils.MIN_LOCK_PATTERN_SIZE) {
                        updateStage(Stage.ChoiceTooShort);
                    } else {
                        mChosenPattern = new ArrayList<LockPatternView.Cell>(pattern);
                        updateStage(Stage.FirstChoiceValid);
                    }
                }
            };

    /**
     * Factory method for creating ChooseLockPatternFragment
     */
    public static ChooseLockPatternFragment newInstance() {
        ChooseLockPatternFragment patternFragment = new ChooseLockPatternFragment();
        return patternFragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    @LayoutRes
    protected int getLayoutId() {
        return R.layout.choose_lock_pattern;
    }

    @Override
    @StringRes
    protected int getTitleId() {
        return R.string.security_lock_pattern;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWrongPatternClearTimeOut = getResources().getInteger(R.integer.clear_content_timeout_ms);
        mUserId = UserHandle.myUserId();

        Bundle args = getArguments();
        if (args != null) {
            mCurrentPattern = args.getByteArray(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK);
        }

        if (savedInstanceState != null) {
            mUiStage = Stage.values()[savedInstanceState.getInt(STATE_UI_STAGE)];
            mChosenPattern = LockPatternUtils.byteArrayToPattern(
                    savedInstanceState.getByteArray(STATE_CHOSEN_PATTERN));
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessageText = view.findViewById(R.id.description_text);
        mMessageText.setText(getString(R.string.choose_lock_pattern_message));

        mLockPatternView = view.findViewById(R.id.lockPattern);
        mLockPatternView.setVisibility(View.VISIBLE);
        mLockPatternView.setEnabled(true);
        mLockPatternView.setFadePattern(false);
        mLockPatternView.clearPattern();
        mLockPatternView.setOnPatternListener(mChooseNewLockPatternListener);

        // Re-attach to the exiting worker if there is one.
        if (savedInstanceState != null) {
            mSavePatternWorker = (SavePatternWorker) getFragmentManager().findFragmentByTag(
                    FRAGMENT_TAG_SAVE_PATTERN_WORKER);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mProgressBar = requireActivity().findViewById(R.id.progress_bar);

        mPrimaryButton = requireActivity().findViewById(R.id.action_button1);
        mPrimaryButton.setOnClickListener(view -> handlePrimaryButtonClick());
        mSecondaryButton = requireActivity().findViewById(R.id.action_button2);
        mSecondaryButton.setVisibility(View.VISIBLE);
        mSecondaryButton.setOnClickListener(view -> handleSecondaryButtonClick());
    }

    @Override
    public void onStart() {
        super.onStart();
        updateStage(mUiStage);

        if (mSavePatternWorker != null) {
            setPrimaryButtonEnabled(true);
            mSavePatternWorker.setListener(this::onChosenLockSaveFinished);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_UI_STAGE, mUiStage.ordinal());
        outState.putByteArray(STATE_CHOSEN_PATTERN,
                LockPatternUtils.patternToByteArray(mChosenPattern));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSavePatternWorker != null) {
            mSavePatternWorker.setListener(null);
        }
        mProgressBar.setVisibility(View.GONE);
    }

    /**
     * Updates the messages and buttons appropriate to what stage the user
     * is at in choosing a pattern. This doesn't handle clearing out the pattern;
     * the pattern is expected to be in the right state.
     *
     * @param stage The stage UI should be updated to match with.
     */
    protected void updateStage(Stage stage) {
        mUiStage = stage;

        // Message mText, visibility and
        // mEnabled state all known from the stage
        mMessageText.setText(stage.mMessageId);

        if (stage.mSecondaryButtonState == SecondaryButtonState.Gone) {
            setSecondaryButtonVisible(false);
        } else {
            setSecondaryButtonVisible(true);
            setSecondaryButtonText(stage.mSecondaryButtonState.mTextResId);
            setSecondaryButtonEnabled(stage.mSecondaryButtonState.mEnabled);
        }

        setPrimaryButtonText(stage.mPrimaryButtonState.mText);
        setPrimaryButtonEnabled(stage.mPrimaryButtonState.mEnabled);

        // same for whether the pattern is mEnabled
        if (stage.mPatternEnabled) {
            mLockPatternView.enableInput();
        } else {
            mLockPatternView.disableInput();
        }

        // the rest of the stuff varies enough that it is easier just to handle
        // on a case by case basis.
        mLockPatternView.setDisplayMode(DisplayMode.Correct);

        switch (mUiStage) {
            case Introduction:
                mLockPatternView.clearPattern();
                break;
            case HelpScreen:
                mLockPatternView.setPattern(DisplayMode.Animate, mAnimatePattern);
                break;
            case ChoiceTooShort:
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                postClearPatternRunnable();
                break;
            case FirstChoiceValid:
                break;
            case NeedToConfirm:
                mLockPatternView.clearPattern();
                break;
            case ConfirmWrong:
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                postClearPatternRunnable();
                break;
            case ChoiceConfirmed:
                break;
            default:
                // Do nothing.
        }
    }

    private void updateUIWhenPatternInProgress() {
        mMessageText.setText(R.string.lockpattern_recording_inprogress);
        setPrimaryButtonEnabled(false);
        setSecondaryButtonEnabled(false);
    }

    // clear the wrong pattern unless they have started a new one
    // already
    private void postClearPatternRunnable() {
        mLockPatternView.removeCallbacks(mClearPatternRunnable);
        mLockPatternView.postDelayed(mClearPatternRunnable, mWrongPatternClearTimeOut);
    }

    private void setPrimaryButtonEnabled(boolean enabled) {
        mPrimaryButton.setEnabled(enabled);
    }

    private void setPrimaryButtonText(@StringRes int textId) {
        mPrimaryButton.setText(textId);
    }

    private void setSecondaryButtonVisible(boolean visible) {
        mSecondaryButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setSecondaryButtonEnabled(boolean enabled) {
        mSecondaryButton.setEnabled(enabled);
    }

    private void setSecondaryButtonText(@StringRes int textId) {
        mSecondaryButton.setText(textId);
    }

    // Update display message and decide on next step according to the different mText
    // on the primary button
    private void handlePrimaryButtonClick() {
        switch (mUiStage.mPrimaryButtonState) {
            case Continue:
                if (mUiStage != Stage.FirstChoiceValid) {
                    throw new IllegalStateException("expected ui stage "
                            + Stage.FirstChoiceValid + " when button is "
                            + PrimaryButtonState.Continue);
                }
                updateStage(Stage.NeedToConfirm);
                break;
            case Confirm:
                if (mUiStage != Stage.ChoiceConfirmed) {
                    throw new IllegalStateException("expected ui stage " + Stage.ChoiceConfirmed
                            + " when button is " + PrimaryButtonState.Confirm);
                }
                startSaveAndFinish();
                break;
            case Retry:
                if (mUiStage != Stage.SaveFailure) {
                    throw new IllegalStateException("expected ui stage " + Stage.SaveFailure
                            + " when button is " + PrimaryButtonState.Retry);
                }
                startSaveAndFinish();
                break;
            case Ok:
                if (mUiStage != Stage.HelpScreen) {
                    throw new IllegalStateException("Help screen is only mode with ok button, "
                            + "but stage is " + mUiStage);
                }
                mLockPatternView.clearPattern();
                mLockPatternView.setDisplayMode(DisplayMode.Correct);
                updateStage(Stage.Introduction);
                break;
            default:
                // Do nothing.
        }
    }

    // Update display message and proceed to next step according to the different mText on
    // the secondary button.
    private void handleSecondaryButtonClick() {
        switch (mUiStage.mSecondaryButtonState) {
            case Retry:
                mChosenPattern = null;
                mLockPatternView.clearPattern();
                updateStage(Stage.Introduction);
                break;
            case Cancel:
                getFragmentController().goBack();
                break;
            default:
                throw new IllegalStateException("secondary footer button pressed, but stage of "
                        + mUiStage + " doesn't make sense");
        }
    }

    @VisibleForTesting
    void onChosenLockSaveFinished(boolean isSaveSuccessful) {
        mProgressBar.setVisibility(View.GONE);

        if (isSaveSuccessful) {
            onComplete();
        } else {
            updateStage(Stage.SaveFailure);
        }
    }

    // Save recorded pattern as an async task and proceed to next
    private void startSaveAndFinish() {
        if (mSavePatternWorker != null && !mSavePatternWorker.isFinished()) {
            LOG.v("startSaveAndFinish with a running SavePatternWorker.");
            return;
        }

        setPrimaryButtonEnabled(false);

        if (mSavePatternWorker == null) {
            mSavePatternWorker = new SavePatternWorker();
            mSavePatternWorker.setListener(this::onChosenLockSaveFinished);

            getFragmentManager()
                    .beginTransaction()
                    .add(mSavePatternWorker, FRAGMENT_TAG_SAVE_PATTERN_WORKER)
                    .commitNow();
        }

        mSavePatternWorker.start(mUserId, mChosenPattern, mCurrentPattern);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @VisibleForTesting
    void onComplete() {
        if (mCurrentPattern != null) {
            Arrays.fill(mCurrentPattern, (byte) 0);
        }

        getActivity().finish();
    }

    /**
     * Keep track internally of where the user is in choosing a pattern.
     */
    enum Stage {
        /**
         * Initial stage when first launching choose a lock pattern.
         * Pattern mEnabled, secondary button allow for Cancel, primary button disabled.
         */
        Introduction(
                R.string.lockpattern_recording_intro_header,
                SecondaryButtonState.Cancel,
                PrimaryButtonState.ContinueDisabled,
                /* patternEnabled= */ true),
        /**
         * Help screen to show how a valid pattern looks like.
         * Pattern disabled, primary button shows Ok. No secondary button.
         */
        HelpScreen(
                R.string.lockpattern_settings_help_how_to_record,
                SecondaryButtonState.Gone,
                PrimaryButtonState.Ok,
                /* patternEnabled= */ false),
        /**
         * Invalid pattern is entered, hint message show required number of dots.
         * Secondary button allows for Retry, primary button disabled.
         */
        ChoiceTooShort(
                R.string.lockpattern_recording_incorrect_too_short,
                SecondaryButtonState.Retry,
                PrimaryButtonState.ContinueDisabled,
                /* patternEnabled= */ true),
        /**
         * First drawing on the pattern is valid, primary button shows Continue,
         * can proceed to next screen.
         */
        FirstChoiceValid(
                R.string.lockpattern_recording_intro_header,
                SecondaryButtonState.Retry,
                PrimaryButtonState.Continue,
                /* patternEnabled= */ false),
        /**
         * Need to draw pattern again to confirm.
         * Secondary button allows for Cancel, primary button disabled.
         */
        NeedToConfirm(
                R.string.lockpattern_need_to_confirm,
                SecondaryButtonState.Cancel,
                PrimaryButtonState.ConfirmDisabled,
                /* patternEnabled= */ true),
        /**
         * Confirmation of previous drawn pattern failed, didn't enter the same pattern.
         * Need to re-draw the pattern to match the fist pattern.
         */
        ConfirmWrong(
                R.string.lockpattern_pattern_wrong,
                SecondaryButtonState.Cancel,
                PrimaryButtonState.ConfirmDisabled,
                /* patternEnabled= */ true),
        /**
         * Pattern is confirmed after drawing the same pattern twice.
         * Pattern disabled.
         */
        ChoiceConfirmed(
                R.string.lockpattern_pattern_confirmed,
                SecondaryButtonState.Cancel,
                PrimaryButtonState.Confirm,
                /* patternEnabled= */ false),

        /**
         * Error saving pattern.
         * Pattern disabled, primary button shows Retry, secondary button allows for cancel
         */
        SaveFailure(
                R.string.error_saving_lockpattern,
                SecondaryButtonState.Cancel,
                PrimaryButtonState.Retry,
                /* patternEnabled= */ false);

        final int mMessageId;
        final SecondaryButtonState mSecondaryButtonState;
        final PrimaryButtonState mPrimaryButtonState;
        final boolean mPatternEnabled;

        /**
         * @param messageId            The message displayed as instruction.
         * @param secondaryButtonState The state of the secondary button.
         * @param primaryButtonState   The state of the primary button.
         * @param patternEnabled       Whether the pattern widget is mEnabled.
         */
        Stage(@StringRes int messageId,
                SecondaryButtonState secondaryButtonState,
                PrimaryButtonState primaryButtonState,
                boolean patternEnabled) {
            this.mMessageId = messageId;
            this.mSecondaryButtonState = secondaryButtonState;
            this.mPrimaryButtonState = primaryButtonState;
            this.mPatternEnabled = patternEnabled;
        }
    }

    /**
     * The states of the primary footer button.
     */
    enum PrimaryButtonState {
        Continue(R.string.continue_button_text, true),
        ContinueDisabled(R.string.continue_button_text, false),
        Confirm(R.string.lockpattern_confirm_button_text, true),
        ConfirmDisabled(R.string.lockpattern_confirm_button_text, false),
        Retry(R.string.lockscreen_retry_button_text, true),
        Ok(R.string.okay, true);

        final int mText;
        final boolean mEnabled;

        /**
         * @param text    The displayed mText for this mode.
         * @param enabled Whether the button should be mEnabled.
         */
        PrimaryButtonState(@StringRes int text, boolean enabled) {
            this.mText = text;
            this.mEnabled = enabled;
        }
    }

    /**
     * The states of the secondary footer button.
     */
    enum SecondaryButtonState {
        Cancel(R.string.lockpattern_cancel_button_text, true),
        CancelDisabled(R.string.lockpattern_cancel_button_text, false),
        Retry(R.string.lockpattern_retry_button_text, true),
        RetryDisabled(R.string.lockpattern_retry_button_text, false),
        Gone(ID_EMPTY_MESSAGE, false);

        final int mTextResId;
        final boolean mEnabled;

        /**
         * @param textId  The displayed mText for this mode.
         * @param enabled Whether the button should be mEnabled.
         */
        SecondaryButtonState(@StringRes int textId, boolean enabled) {
            this.mTextResId = textId;
            this.mEnabled = enabled;
        }
    }
}
