/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.usb.handler;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

/**
 * This class is used to describe a USB device. When used in HashMaps all values must be specified,
 *  but wildcards can be used for any of the fields in the package meta-data.
 */
class UsbDeviceFilter {
    private static final String TAG = UsbDeviceFilter.class.getSimpleName();

    // USB Vendor ID (or -1 for unspecified)
    public final int mVendorId;
    // USB Product ID (or -1 for unspecified)
    public final int mProductId;
    // USB device or interface class (or -1 for unspecified)
    public final int mClass;
    // USB device subclass (or -1 for unspecified)
    public final int mSubclass;
    // USB device protocol (or -1 for unspecified)
    public final int mProtocol;
    // USB device manufacturer name string (or null for unspecified)
    public final String mManufacturerName;
    // USB device product name string (or null for unspecified)
    public final String mProductName;
    // USB device serial number string (or null for unspecified)
    public final String mSerialNumber;

    // USB device in AOAP mode manufacturer
    public final String mAoapManufacturer;
    // USB device in AOAP mode model
    public final String mAoapModel;
    // USB device in AOAP mode description string
    public final String mAoapDescription;
    // USB device in AOAP mode version
    public final String mAoapVersion;
    // USB device in AOAP mode URI
    public final String mAoapUri;
    // USB device in AOAP mode serial
    public final String mAoapSerial;
    // USB device in AOAP mode verification service
    public final String mAoapService;

    UsbDeviceFilter(int vid, int pid, int clasz, int subclass, int protocol,
                        String manufacturer, String product, String serialnum,
                        String aoapManufacturer, String aoapModel, String aoapDescription,
                        String aoapVersion, String aoapUri, String aoapSerial,
                        String aoapService) {
        mVendorId = vid;
        mProductId = pid;
        mClass = clasz;
        mSubclass = subclass;
        mProtocol = protocol;
        mManufacturerName = manufacturer;
        mProductName = product;
        mSerialNumber = serialnum;

        mAoapManufacturer = aoapManufacturer;
        mAoapModel = aoapModel;
        mAoapDescription = aoapDescription;
        mAoapVersion = aoapVersion;
        mAoapUri = aoapUri;
        mAoapSerial = aoapSerial;
        mAoapService = aoapService;
    }

    public static UsbDeviceFilter read(XmlPullParser parser, boolean aoapData) {
        int vendorId = -1;
        int productId = -1;
        int deviceClass = -1;
        int deviceSubclass = -1;
        int deviceProtocol = -1;
        String manufacturerName = null;
        String productName = null;
        String serialNumber = null;

        String aoapManufacturer = null;
        String aoapModel = null;
        String aoapDescription = null;
        String aoapVersion = null;
        String aoapUri = null;
        String aoapSerial = null;
        String aoapService = null;

        int count = parser.getAttributeCount();
        for (int i = 0; i < count; i++) {
            String name = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            // Attribute values are ints or strings
            if (!aoapData && "manufacturer-name".equals(name)) {
                manufacturerName = value;
            } else if (!aoapData && "product-name".equals(name)) {
                productName = value;
            } else if (!aoapData && "serial-number".equals(name)) {
                serialNumber = value;
            } else if (aoapData && "manufacturer".equals(name)) {
                aoapManufacturer = value;
            } else if (aoapData && "model".equals(name)) {
                aoapModel = value;
            } else if (aoapData && "description".equals(name)) {
                aoapDescription = value;
            } else if (aoapData && "version".equals(name)) {
                aoapVersion = value;
            } else if (aoapData && "uri".equals(name)) {
                aoapUri = value;
            } else if (aoapData && "serial".equals(name)) {
                aoapSerial = value;
            } else if (aoapData && "service".equals(name)) {
                aoapService = value;
            } else if (!aoapData) {
                int intValue = -1;
                int radix = 10;
                if (value != null && value.length() > 2 && value.charAt(0) == '0'
                        && (value.charAt(1) == 'x' || value.charAt(1) == 'X')) {
                    // allow hex values starting with 0x or 0X
                    radix = 16;
                    value = value.substring(2);
                }
                try {
                    intValue = Integer.parseInt(value, radix);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "invalid number for field " + name, e);
                    continue;
                }
                if ("vendor-id".equals(name)) {
                    vendorId = intValue;
                } else if ("product-id".equals(name)) {
                    productId = intValue;
                } else if ("class".equals(name)) {
                    deviceClass = intValue;
                } else if ("subclass".equals(name)) {
                    deviceSubclass = intValue;
                } else if ("protocol".equals(name)) {
                    deviceProtocol = intValue;
                }
            }
        }
        return new UsbDeviceFilter(vendorId, productId,
                                deviceClass, deviceSubclass, deviceProtocol,
                                manufacturerName, productName, serialNumber, aoapManufacturer,
                                aoapModel, aoapDescription, aoapVersion, aoapUri, aoapSerial,
                                aoapService);
    }

    private boolean matches(int clasz, int subclass, int protocol) {
        return ((mClass == -1 || clasz == mClass)
                && (mSubclass == -1 || subclass == mSubclass)
                && (mProtocol == -1 || protocol == mProtocol));
    }

    public boolean matches(UsbDevice device) {
        if (mVendorId != -1 && device.getVendorId() != mVendorId) {
            return false;
        }
        if (mProductId != -1 && device.getProductId() != mProductId) {
            return false;
        }
        if (mManufacturerName != null && device.getManufacturerName() == null) {
            return false;
        }
        if (mProductName != null && device.getProductName() == null) {
            return false;
        }
        if (mSerialNumber != null && device.getSerialNumber() == null) {
            return false;
        }
        if (mManufacturerName != null && device.getManufacturerName() != null
                && !mManufacturerName.equals(device.getManufacturerName())) {
            return false;
        }
        if (mProductName != null && device.getProductName() != null
                && !mProductName.equals(device.getProductName())) {
            return false;
        }
        if (mSerialNumber != null && device.getSerialNumber() != null
                && !mSerialNumber.equals(device.getSerialNumber())) {
            return false;
        }

        // check device class/subclass/protocol
        if (matches(device.getDeviceClass(), device.getDeviceSubclass(),
                    device.getDeviceProtocol())) {
            return true;
        }

        // if device doesn't match, check the interfaces
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (matches(intf.getInterfaceClass(), intf.getInterfaceSubclass(),
                        intf.getInterfaceProtocol())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(Object obj) {
        // can't compare if we have wildcard strings
        if (mVendorId == -1 || mProductId == -1
                || mClass == -1 || mSubclass == -1 || mProtocol == -1) {
            return false;
        }
        if (obj instanceof UsbDeviceFilter) {
            UsbDeviceFilter filter = (UsbDeviceFilter) obj;

            if (filter.mVendorId != mVendorId
                    || filter.mProductId != mProductId
                    || filter.mClass != mClass
                    || filter.mSubclass != mSubclass
                    || filter.mProtocol != mProtocol) {
                return false;
            }
            if ((filter.mManufacturerName != null && mManufacturerName == null)
                    || (filter.mManufacturerName == null && mManufacturerName != null)
                    || (filter.mProductName != null && mProductName == null)
                    || (filter.mProductName == null && mProductName != null)
                    || (filter.mSerialNumber != null && mSerialNumber == null)
                    || (filter.mSerialNumber == null && mSerialNumber != null)) {
                return false;
            }
            if  ((filter.mManufacturerName != null && mManufacturerName != null
                      && !mManufacturerName.equals(filter.mManufacturerName))
                      || (filter.mProductName != null && mProductName != null
                      && !mProductName.equals(filter.mProductName))
                      || (filter.mSerialNumber != null && mSerialNumber != null
                      && !mSerialNumber.equals(filter.mSerialNumber))) {
                return false;
            }
            return true;
        }
        if (obj instanceof UsbDevice) {
            UsbDevice device = (UsbDevice) obj;
            if (device.getVendorId() != mVendorId
                    || device.getProductId() != mProductId
                    || device.getDeviceClass() != mClass
                    || device.getDeviceSubclass() != mSubclass
                    || device.getDeviceProtocol() != mProtocol) {
                return false;
            }
            if ((mManufacturerName != null && device.getManufacturerName() == null)
                    || (mManufacturerName == null && device.getManufacturerName() != null)
                    || (mProductName != null && device.getProductName() == null)
                    || (mProductName == null && device.getProductName() != null)
                    || (mSerialNumber != null && device.getSerialNumber() == null)
                    || (mSerialNumber == null && device.getSerialNumber() != null)) {
                return false;
            }
            if ((device.getManufacturerName() != null
                    && !mManufacturerName.equals(device.getManufacturerName()))
                    || (device.getProductName() != null
                    && !mProductName.equals(device.getProductName()))
                    || (device.getSerialNumber() != null
                    && !mSerialNumber.equals(device.getSerialNumber()))) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (((mVendorId << 16) | mProductId)
                ^ ((mClass << 16) | (mSubclass << 8) | mProtocol));
    }

    @Override
    public String toString() {
        return "DeviceFilter[mVendorId=" + mVendorId + ",mProductId=" + mProductId
                + ",mClass=" + mClass + ",mSubclass=" + mSubclass
                + ",mProtocol=" + mProtocol + ",mManufacturerName=" + mManufacturerName
                + ",mProductName=" + mProductName + ",mSerialNumber=" + mSerialNumber + "]";
    }
}
