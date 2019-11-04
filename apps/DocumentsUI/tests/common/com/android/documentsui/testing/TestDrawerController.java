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

package com.android.documentsui.testing;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.documentsui.DrawerController;

import org.mockito.Mockito;

/**
 * We use abstract so we don't have to implement all the necessary methods from the interface.
 * To get an instance, use {@link #create()}.
 */
public abstract class TestDrawerController extends DrawerController {

    public static TestDrawerController create() {
        TestDrawerController drawController = Mockito.mock(
                TestDrawerController.class);
        Mockito.doCallRealMethod().when(drawController).openDrawer(Mockito.anyBoolean());
        Mockito.doCallRealMethod().when(drawController).assertWasOpened();
        Mockito.doCallRealMethod().when(drawController).assertWasClosed();

        return drawController;
    }

    public void openDrawer(boolean open) {
        when(isPresent()).thenReturn(open);
        when(isOpen()).thenReturn(open);
    }

    public void assertWasOpened() {
        verify(this).setOpen(false);
    }

    public void assertWasClosed() {
        verify(this, never()).setOpen(false);
    }
}