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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Intent;
import android.os.UserHandle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

/**
 * Provides a means of wrapping {@code RoleManager} operations which Telecom uses to aid in testing
 * and remove direct dependencies.
 */
public interface RoleManagerAdapter {

    /**
     * The name of the dialer role.
     *
     * @see Intent#ACTION_DIAL
     */
    String ROLE_DIALER = "android.app.role.DIALER";

    /**
     * Returns the package name of the app which fills the {@link android.app.role.RoleManager} call
     * redirection role.
     * @return the package name of the app filling the role, {@code null} otherwise}.
     */
    String getDefaultCallRedirectionApp();

    /**
     * Override the {@link android.app.role.RoleManager} call redirection app with another value.
     * Used for testing purposes only.
     * @param packageName Package name of the app to fill the call redirection role.  Where
     *                    {@code null}, the override is removed.
     */
    void setTestDefaultCallRedirectionApp(String packageName);

    /**
     * Returns the package name of the app which fills the {@link android.app.role.RoleManager} call
     * screening role.
     * @return the package name of the app filling the role, {@code null} otherwise}.
     */
    String getDefaultCallScreeningApp();

    /**
     * Override the {@link android.app.role.RoleManager} call screening app with another value.
     * Used for testing purposes only.
     * @param packageName Package name of the app to fill the call screening role.  Where
     *                    {@code null}, the override is removed.
     */
    void setTestDefaultCallScreeningApp(String packageName);

    /**
     * Returns the package name of the app which fills the {@link android.app.role.RoleManager}
     * {@link android.app.role.RoleManager#ROLE_DIALER} role.
     * @return the package name of the app filling the role, {@code null} otherwise}.
     */
    String getDefaultDialerApp(int user);

    /**
     * Observe changes to the package name of the app which fills the
     * {@link android.app.role.RoleManager} {@link android.app.role.RoleManager#ROLE_DIALER} role.
     */
    void observeDefaultDialerApp(Executor executor, IntConsumer observer);

    /**
     * Override the {@link android.app.role.RoleManager} default dialer app with another value.
     * Used for testing purposes only.
     * @param packageName Package name of the app to fill the default dialer role.  Where
     *                    {@code null}, the override is removed.
     */
    void setTestDefaultDialer(String packageName);

    /**
     * @return List of package names of companion apps, or empty list if there are none.
     */
    List<String> getCallCompanionApps();

    /**
     * Set a package to be added to the list of the companion apps. Used for testing purposes only.
     * @param packageName Package name of the app to be added or removed as an override call
     *                    companion app.
     * @param isAdded {@code true} if the specified package should be added, {@code false} if it
     *                            should be removed.
     */
    void addOrRemoveTestCallCompanionApp(String packageName, boolean isAdded);

    /**
     * @return Package name of the car more app or {@code null} if there are no apps that match.
     */
    String getCarModeDialerApp();

    /**
     * Override the automotive app with another value. Used for testing purposes only.
     * @param packageName Package name of the automotive app. Where
     *                    {@code null}, the override is removed.
     */
    void setTestAutoModeApp(String packageName);

    /**
     * Using role manager needs to know the current user handle.  Need to make sure the role manager
     * adapter can pass this to role manager.  As it changes, we'll pass it in.
     * @param currentUserHandle The new user handle.
     */
    void setCurrentUserHandle(UserHandle currentUserHandle);

    /**
     * Returns the application label that corresponds to the given package name.
     * @param packageName A valid package name.
     * @return Application label for the given package name, or null if not found.
     */
    String getApplicationLabelForPackageName(String packageName);
}
