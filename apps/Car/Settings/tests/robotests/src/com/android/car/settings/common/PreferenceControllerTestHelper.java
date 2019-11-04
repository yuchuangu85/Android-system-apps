/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.common;

import static androidx.lifecycle.Lifecycle.Event.ON_CREATE;
import static androidx.lifecycle.Lifecycle.Event.ON_DESTROY;
import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;
import static androidx.lifecycle.Lifecycle.Event.ON_START;
import static androidx.lifecycle.Lifecycle.Event.ON_STOP;
import static androidx.lifecycle.Lifecycle.State.CREATED;
import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.INITIALIZED;
import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import static org.mockito.Mockito.mock;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * Helper for testing {@link PreferenceController} classes.
 *
 * @param <T> the type of preference controller under test.
 */
public class PreferenceControllerTestHelper<T extends PreferenceController> {

    private static final String PREFERENCE_KEY = "preference_key";

    private static final CarUxRestrictions UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

    private Lifecycle.State mState = INITIALIZED;

    private final FragmentController mMockFragmentController;
    private final T mPreferenceController;
    private final PreferenceScreen mScreen;
    private boolean mSetPreferenceCalled;

    /**
     * Constructs a new helper. Call {@link #setPreference(Preference)} once initialization on the
     * controller is complete to associate the controller with a preference.
     *
     * @param context                  the {@link Context} to use to instantiate the preference
     *                                 controller.
     * @param preferenceControllerType the class type under test.
     */
    public PreferenceControllerTestHelper(Context context, Class<T> preferenceControllerType) {
        mMockFragmentController = mock(FragmentController.class);
        mPreferenceController = ReflectionHelpers.callConstructor(preferenceControllerType,
                ClassParameter.from(Context.class, context),
                ClassParameter.from(String.class, PREFERENCE_KEY),
                ClassParameter.from(FragmentController.class, mMockFragmentController),
                ClassParameter.from(CarUxRestrictions.class, UX_RESTRICTIONS));
        mScreen = new PreferenceManager(context).createPreferenceScreen(context);
    }

    /**
     * Convenience constructor for a new helper for controllers which do not need to do additional
     * initialization before a preference is set.
     *
     * @param preference the {@link Preference} to associate with the controller.
     */
    public PreferenceControllerTestHelper(Context context, Class<T> preferenceControllerType,
            Preference preference) {
        this(context, preferenceControllerType);
        setPreference(preference);
    }

    /**
     * Associates the controller with the given preference. This should only be called once.
     */
    public void setPreference(Preference preference) {
        if (mSetPreferenceCalled) {
            throw new IllegalStateException(
                    "setPreference should only be called once. Create a new helper if needed.");
        }
        preference.setKey(PREFERENCE_KEY);
        mScreen.addPreference(preference);
        mPreferenceController.setPreference(preference);
        mSetPreferenceCalled = true;
    }

    /**
     * Returns the {@link PreferenceController} of this helper.
     */
    public T getController() {
        return mPreferenceController;
    }

    /**
     * Returns a mock {@link FragmentController} that can be used to verify controller navigation
     * and stub finding dialog fragments.
     */
    public FragmentController getMockFragmentController() {
        return mMockFragmentController;
    }

    /**
     * Sends a {@link Lifecycle.Event} to the controller. This is preferred over calling the
     * controller's lifecycle methods directly as it ensures intermediate events are dispatched.
     * For example, sending {@link Lifecycle.Event#ON_START} to an
     * {@link Lifecycle.State#INITIALIZED} controller will dispatch
     * {@link Lifecycle.Event#ON_CREATE} and {@link Lifecycle.Event#ON_START} while moving the
     * controller to the {@link Lifecycle.State#STARTED} state.
     */
    public void sendLifecycleEvent(Lifecycle.Event event) {
        markState(getStateAfter(event));
    }

    /**
     * Move the {@link PreferenceController} to the given {@code state}. This is preferred over
     * calling the controller's lifecycle methods directly as it ensures intermediate events are
     * dispatched. For example, marking the {@link Lifecycle.State#STARTED} state on an
     * {@link Lifecycle.State#INITIALIZED} controller will also send the
     * {@link Lifecycle.Event#ON_CREATE} and {@link Lifecycle.Event#ON_START} events.
     */
    public void markState(Lifecycle.State state) {
        while (mState != state) {
            while (mState.compareTo(state) > 0) {
                dispatchEvent(downEvent(mState));
            }
            while (mState.compareTo(state) < 0) {
                dispatchEvent(upEvent(mState));
            }
        }
    }

    public static String getKey() {
        return PREFERENCE_KEY;
    }

    /*
     * Ideally we would use androidx.lifecycle.LifecycleRegistry to drive the lifecycle changes.
     * However, doing so led to test flakiness with an unknown root cause. We dispatch state
     * changes manually for now, borrowing from LifecycleRegistry's implementation, pending
     * further investigation.
     */

    @NonNull
    private Lifecycle getLifecycle() {
        throw new UnsupportedOperationException();
    }

    private void dispatchEvent(Lifecycle.Event event) {
        switch (event) {
            case ON_CREATE:
                mScreen.onAttached();
                mPreferenceController.onCreate(this::getLifecycle);
                break;
            case ON_START:
                mPreferenceController.onStart(this::getLifecycle);
                break;
            case ON_RESUME:
                mPreferenceController.onResume(this::getLifecycle);
                break;
            case ON_PAUSE:
                mPreferenceController.onPause(this::getLifecycle);
                break;
            case ON_STOP:
                mPreferenceController.onStop(this::getLifecycle);
                break;
            case ON_DESTROY:
                mScreen.onDetached();
                mPreferenceController.onDestroy(this::getLifecycle);
                break;
            case ON_ANY:
                throw new IllegalArgumentException();
        }

        mState = getStateAfter(event);
    }

    private static Lifecycle.State getStateAfter(Lifecycle.Event event) {
        switch (event) {
            case ON_CREATE:
            case ON_STOP:
                return CREATED;
            case ON_START:
            case ON_PAUSE:
                return STARTED;
            case ON_RESUME:
                return RESUMED;
            case ON_DESTROY:
                return DESTROYED;
            case ON_ANY:
                break;
        }
        throw new IllegalArgumentException("Unexpected event value " + event);
    }

    private static Lifecycle.Event downEvent(Lifecycle.State state) {
        switch (state) {
            case INITIALIZED:
                throw new IllegalArgumentException();
            case CREATED:
                return ON_DESTROY;
            case STARTED:
                return ON_STOP;
            case RESUMED:
                return ON_PAUSE;
            case DESTROYED:
                throw new IllegalArgumentException();
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }

    private static Lifecycle.Event upEvent(Lifecycle.State state) {
        switch (state) {
            case INITIALIZED:
            case DESTROYED:
                return ON_CREATE;
            case CREATED:
                return ON_START;
            case STARTED:
                return ON_RESUME;
            case RESUMED:
                throw new IllegalArgumentException();
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }
}
