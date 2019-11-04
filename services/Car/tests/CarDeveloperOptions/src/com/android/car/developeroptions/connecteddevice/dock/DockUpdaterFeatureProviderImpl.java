package com.android.car.developeroptions.connecteddevice.dock;

import android.content.Context;

import com.android.car.developeroptions.connecteddevice.DevicePreferenceCallback;
import com.android.car.developeroptions.overlay.DockUpdaterFeatureProvider;

/**
 * Impl for {@link DockUpdaterFeatureProvider}
 */
public class DockUpdaterFeatureProviderImpl implements DockUpdaterFeatureProvider {

    @Override
    public DockUpdater getConnectedDockUpdater(Context context,
            DevicePreferenceCallback devicePreferenceCallback) {
        final DockUpdater updater = new DockUpdater() {
        };
        return updater;
    }

    @Override
    public DockUpdater getSavedDockUpdater(Context context,
            DevicePreferenceCallback devicePreferenceCallback) {
        final DockUpdater updater = new DockUpdater() {
        };
        return updater;
    }
}
