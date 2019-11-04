package android.car.usb.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.HashMap;

/** Queues work to the BootUsbService job to scan for connected devices. */
public class BootUsbScanner extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only start the service if we are the system user. We cannot use the singleUser tag in the
        // manifest for <receiver>s, so there is no way to avoid us registering this receiver as the
        // non system user. Just return immediately if we are not.
        if (context.getUserId() != UserHandle.USER_SYSTEM) {
            return;
        }
        // we defer this processing to BootUsbService so that we are very quick to process
        // LOCKED_BOOT_COMPLETED
        UsbManager usbManager = context.getSystemService(UsbManager.class);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.size() > 0) {
            Intent bootUsbServiceIntent = new Intent(context, BootUsbService.class);
            bootUsbServiceIntent.putParcelableArrayListExtra(
                    BootUsbService.USB_DEVICE_LIST_KEY, new ArrayList<>(deviceList.values()));

            context.startForegroundService(bootUsbServiceIntent);
        }
    }
}
