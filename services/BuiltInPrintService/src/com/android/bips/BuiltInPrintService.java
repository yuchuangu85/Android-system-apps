/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.print.PrinterId;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.text.TextUtils;
import android.util.Log;

import com.android.bips.discovery.DelayedDiscovery;
import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.discovery.Discovery;
import com.android.bips.discovery.ManualDiscovery;
import com.android.bips.discovery.MdnsDiscovery;
import com.android.bips.discovery.MultiDiscovery;
import com.android.bips.discovery.NsdResolveQueue;
import com.android.bips.discovery.P2pDiscovery;
import com.android.bips.ipp.Backend;
import com.android.bips.ipp.CapabilitiesCache;
import com.android.bips.ipp.CertificateStore;
import com.android.bips.p2p.P2pMonitor;
import com.android.bips.p2p.P2pUtils;
import com.android.bips.util.BroadcastMonitor;

import java.lang.ref.WeakReference;

public class BuiltInPrintService extends PrintService {
    private static final String TAG = BuiltInPrintService.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int IPPS_PRINTER_DELAY = 150;
    private static final int P2P_DISCOVERY_DELAY = 1000;
    private static final String CHANNEL_ID_SECURITY = "security";
    private static final String TAG_CERTIFICATE_REQUEST =
            BuiltInPrintService.class.getCanonicalName() + ".CERTIFICATE_REQUEST";
    private static final String ACTION_CERTIFICATE_ACCEPT =
            BuiltInPrintService.class.getCanonicalName() + ".CERTIFICATE_ACCEPT";
    private static final String ACTION_CERTIFICATE_REJECT =
            BuiltInPrintService.class.getCanonicalName() + ".CERTIFICATE_REJECT";
    public static final String ACTION_P2P_PERMISSION_CANCEL =
            BuiltInPrintService.class.getCanonicalName() + ".P2P_PERMISSION_CANCEL";
    private static final String EXTRA_CERTIFICATE = "certificate";
    private static final String EXTRA_PRINTER_ID = "printer-id";
    private static final String EXTRA_PRINTER_UUID = "printer-uuid";
    private static final int CERTIFICATE_REQUEST_ID = 1000;
    public static final int P2P_PERMISSION_REQUEST_ID = 1001;

    // Present because local activities can bind, but cannot access this object directly
    private static WeakReference<BuiltInPrintService> sInstance;

    private MultiDiscovery mAllDiscovery;
    private P2pDiscovery mP2pDiscovery;
    private Discovery mMdnsDiscovery;
    private ManualDiscovery mManualDiscovery;
    private CapabilitiesCache mCapabilitiesCache;
    private CertificateStore mCertificateStore;
    private JobQueue mJobQueue;
    private Handler mMainHandler;
    private Backend mBackend;
    private WifiManager.WifiLock mWifiLock;
    private P2pMonitor mP2pMonitor;
    private NsdResolveQueue mNsdResolveQueue;
    private P2pPermissionManager mP2pPermissionManager;

    /**
     * Return the current print service instance, if running
     */
    public static BuiltInPrintService getInstance() {
        return sInstance == null ? null : sInstance.get();
    }

    @Override
    public void onCreate() {
        if (DEBUG) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                Log.d(TAG, "onCreate() " + version);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        super.onCreate();
        createNotificationChannel();
        mP2pPermissionManager = new P2pPermissionManager(this);
        mP2pPermissionManager.reset();

        sInstance = new WeakReference<>(this);
        mBackend = new Backend(this);
        mCertificateStore = new CertificateStore(this);
        mCapabilitiesCache = new CapabilitiesCache(this, mBackend,
                CapabilitiesCache.DEFAULT_MAX_CONCURRENT);
        mP2pMonitor = new P2pMonitor(this);

        NsdManager nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        mNsdResolveQueue = new NsdResolveQueue(this, nsdManager);

        // Delay IPP results so that IPP is preferred
        Discovery ippDiscovery = new MdnsDiscovery(this, MdnsDiscovery.SCHEME_IPP);
        Discovery ippsDiscovery = new MdnsDiscovery(this, MdnsDiscovery.SCHEME_IPPS);
        mMdnsDiscovery = new MultiDiscovery(ippDiscovery, new DelayedDiscovery(ippsDiscovery, 0,
                IPPS_PRINTER_DELAY));
        mP2pDiscovery = new P2pDiscovery(this);
        mManualDiscovery = new ManualDiscovery(this);

        // Delay P2P discovery so that all others are found first
        mAllDiscovery = new MultiDiscovery(mMdnsDiscovery, mManualDiscovery, new DelayedDiscovery(
                mP2pDiscovery, P2P_DISCOVERY_DELAY, 0));

        mJobQueue = new JobQueue();
        mMainHandler = new Handler(getMainLooper());
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        mP2pPermissionManager.closeNotification();
        mCapabilitiesCache.close();
        mP2pMonitor.stopAll();
        mBackend.close();
        unlockWifi();
        sInstance = null;
        mMainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        if (DEBUG) Log.d(TAG, "onCreatePrinterDiscoverySession");
        return new LocalDiscoverySession(this);
    }

    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        if (DEBUG) Log.d(TAG, "onPrintJobQueued");
        mJobQueue.print(new LocalPrintJob(this, mBackend, printJob));
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        if (DEBUG) Log.d(TAG, "onRequestCancelPrintJob");
        mJobQueue.cancel(printJob.getId());
    }

    /**
     * Return the global discovery object
     */
    public Discovery getDiscovery() {
        return mAllDiscovery;
    }

    /**
     * Return the global object for MDNS discoveries
     */
    public Discovery getMdnsDiscovery() {
        return mMdnsDiscovery;
    }

    /**
     * Return the global object for manual discoveries
     */
    public ManualDiscovery getManualDiscovery() {
        return mManualDiscovery;
    }

    /**
     * Return the global object for Wi-Fi Direct printer discoveries
     */
    public P2pDiscovery getP2pDiscovery() {
        return mP2pDiscovery;
    }

    /**
     * Return the global object for general Wi-Fi Direct management
     */
    public P2pMonitor getP2pMonitor() {
        return mP2pMonitor;
    }

    /**
     * Return the global {@link NsdResolveQueue}
     */
    public NsdResolveQueue getNsdResolveQueue() {
        return mNsdResolveQueue;
    }

    /**
     * Return a general {@link P2pPermissionManager}
     */
    public P2pPermissionManager getP2pPermissionManager() {
        return mP2pPermissionManager;
    }

    /**
     * Listen for a set of broadcast messages until stopped
     */
    public BroadcastMonitor receiveBroadcasts(BroadcastReceiver receiver, String... actions) {
        return new BroadcastMonitor(this, receiver, actions);
    }

    /**
     * Return the global Printer Capabilities cache
     */
    public CapabilitiesCache getCapabilitiesCache() {
        return mCapabilitiesCache;
    }

    /**
     * Return a store of certificate public keys for supporting trust-on-first-use.
     */
    public CertificateStore getCertificateStore() {
        return mCertificateStore;
    }

    /**
     * Return the main handler for posting {@link Runnable} objects to the main UI
     */
    public Handler getMainHandler() {
        return mMainHandler;
    }

    /** Run something on the main thread, returning an object that can cancel the request */
    public DelayedAction delay(int delay, Runnable toRun) {
        mMainHandler.postDelayed(toRun, delay);
        return () -> mMainHandler.removeCallbacks(toRun);
    }

    /**
     * Return a friendly description string including host and (if present) location
     */
    public String getDescription(DiscoveredPrinter printer) {
        if (P2pUtils.isP2p(printer) || P2pUtils.isOnConnectedInterface(this, printer)) {
            return getString(R.string.wifi_direct);
        }

        String host = printer.getHost();
        if (!TextUtils.isEmpty(printer.location)) {
            return getString(R.string.printer_description, host, printer.location);
        } else {
            return host;
        }
    }

    /** Prevent Wi-Fi from going to sleep until {@link #unlockWifi} is called */
    public void lockWifi() {
        if (!mWifiLock.isHeld()) {
            mWifiLock.acquire();
        }
    }

    /** Allow Wi-Fi to be disabled during sleep modes. */
    public void unlockWifi() {
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    /**
     * Set up a channel for notifications.
     */
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID_SECURITY,
                getString(R.string.security), NotificationManager.IMPORTANCE_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    /**
     * Notify the user of a certificate change (could be a MITM attack) and allow response.
     *
     * When certificate is null, the printer is being downgraded to no-encryption.
     */
    void notifyCertificateChange(String printerName, PrinterId printerId, String printerUuid,
                                 byte[] certificate) {
        String message;
        if (certificate == null) {
            message = getString(R.string.not_encrypted_request);
        } else {
            message = getString(R.string.certificate_update_request);
        }

        Intent rejectIntent = new Intent(this, BuiltInPrintService.class)
                .setAction(ACTION_CERTIFICATE_REJECT)
                .putExtra(EXTRA_PRINTER_ID, printerId);
        PendingIntent pendingRejectIntent = PendingIntent.getService(this, CERTIFICATE_REQUEST_ID,
                rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action rejectAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_printservice),
                getString(R.string.reject), pendingRejectIntent).build();

        PendingIntent deleteIntent = PendingIntent.getService(this, CERTIFICATE_REQUEST_ID,
                rejectIntent, 0);

        Intent acceptIntent = new Intent(this, BuiltInPrintService.class)
                .setAction(ACTION_CERTIFICATE_ACCEPT)
                .putExtra(EXTRA_PRINTER_UUID, printerUuid)
                .putExtra(EXTRA_PRINTER_ID, printerId);
        if (certificate != null) {
            acceptIntent.putExtra(EXTRA_CERTIFICATE, certificate);
        }
        PendingIntent pendingAcceptIntent = PendingIntent.getService(this, CERTIFICATE_REQUEST_ID,
                acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action acceptAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_printservice),
                getString(R.string.accept), pendingAcceptIntent).build();

        Notification notification = new Notification.Builder(this, CHANNEL_ID_SECURITY)
                .setContentTitle(printerName)
                .setSmallIcon(R.drawable.ic_printservice)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentText(message)
                .setAutoCancel(true)
                .addAction(rejectAction)
                .addAction(acceptAction)
                .setDeleteIntent(deleteIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        manager.notify(TAG_CERTIFICATE_REQUEST, CERTIFICATE_REQUEST_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Received action=" + intent.getAction());
        NotificationManager manager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (ACTION_CERTIFICATE_ACCEPT.equals(intent.getAction())) {
            byte[] certificate = intent.getByteArrayExtra(EXTRA_CERTIFICATE);
            PrinterId printerId = intent.getParcelableExtra(EXTRA_PRINTER_ID);
            String printerUuid = intent.getStringExtra(EXTRA_PRINTER_UUID);
            if (certificate != null) {
                mCertificateStore.put(printerUuid, certificate);
            } else {
                mCertificateStore.remove(printerUuid);
            }
            // Restart the job with the updated certificate in place
            mJobQueue.restart(printerId);
            manager.cancel(TAG_CERTIFICATE_REQUEST, CERTIFICATE_REQUEST_ID);
        } else if (ACTION_CERTIFICATE_REJECT.equals(intent.getAction())) {
            // Cancel any job in certificate state for this uuid
            PrinterId printerId = intent.getParcelableExtra(EXTRA_PRINTER_ID);
            mJobQueue.cancel(printerId);
            manager.cancel(TAG_CERTIFICATE_REQUEST, CERTIFICATE_REQUEST_ID);
        } else if (ACTION_P2P_PERMISSION_CANCEL.equals(intent.getAction())) {
            // Inform p2pPermissionManager the user canceled the notification (non-permanent)
            mP2pPermissionManager.applyPermissionChange(false);
        }
        return START_NOT_STICKY;
    }
}
