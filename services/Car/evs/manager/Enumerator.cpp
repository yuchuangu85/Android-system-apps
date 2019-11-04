/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <hwbinder/IPCThreadState.h>
#include <cutils/android_filesystem_config.h>

#include "Enumerator.h"
#include "HalDisplay.h"

namespace android {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


bool Enumerator::init(const char* hardwareServiceName) {
    ALOGD("init");

    // Connect with the underlying hardware enumerator
    mHwEnumerator = IEvsEnumerator::getService(hardwareServiceName);
    bool result = (mHwEnumerator.get() != nullptr);

    return result;
}


bool Enumerator::checkPermission() {
    hardware::IPCThreadState *ipc = hardware::IPCThreadState::self();
    if (AID_AUTOMOTIVE_EVS != ipc->getCallingUid()) {
        ALOGE("EVS access denied: pid = %d, uid = %d", ipc->getCallingPid(), ipc->getCallingUid());
        return false;
    }

    return true;
}


// Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
Return<void> Enumerator::getCameraList(getCameraList_cb list_cb)  {
    ALOGD("getCameraList");
    if (!checkPermission()) {
        return Void();
    }

    // Simply pass through to hardware layer
    return mHwEnumerator->getCameraList(list_cb);
}


Return<sp<IEvsCamera>> Enumerator::openCamera(const hidl_string& cameraId) {
    ALOGD("openCamera");
    if (!checkPermission()) {
        return nullptr;
    }

    // Is the underlying hardware camera already open?
    sp<HalCamera> hwCamera;
    for (auto &&cam : mCameras) {
        bool match = false;
        cam->getHwCamera()->getCameraInfo([cameraId, &match](CameraDesc desc) {
                                      if (desc.cameraId == cameraId) {
                                          match = true;
                                      }
                                  }
        );
        if (match) {
            hwCamera = cam;
            break;
        }
    }

    // Do we need to open a new hardware camera?
    if (hwCamera == nullptr) {
        // Is the hardware camera available?
        sp<IEvsCamera> device = mHwEnumerator->openCamera(cameraId);
        if (device == nullptr) {
            ALOGE("Failed to open hardware camera %s", cameraId.c_str());
        } else {
            hwCamera = new HalCamera(device);
            if (hwCamera == nullptr) {
                ALOGE("Failed to allocate camera wrapper object");
                mHwEnumerator->closeCamera(device);
            }
        }
    }

    // Construct a virtual camera wrapper for this hardware camera
    sp<VirtualCamera> clientCamera;
    if (hwCamera != nullptr) {
        clientCamera = hwCamera->makeVirtualCamera();
    }

    // Add the hardware camera to our list, which will keep it alive via ref count
    if (clientCamera != nullptr) {
        mCameras.push_back(hwCamera);
    } else {
        ALOGE("Requested camera %s not found or not available", cameraId.c_str());
    }

    // Send the virtual camera object back to the client by strong pointer which will keep it alive
    return clientCamera;
}


Return<void> Enumerator::closeCamera(const ::android::sp<IEvsCamera>& clientCamera) {
    ALOGD("closeCamera");

    if (clientCamera.get() == nullptr) {
        ALOGE("Ignoring call with null camera pointer.");
        return Void();
    }

    // All our client cameras are actually VirtualCamera objects
    sp<VirtualCamera> virtualCamera = reinterpret_cast<VirtualCamera*>(clientCamera.get());

    // Find the parent camera that backs this virtual camera
    sp<HalCamera> halCamera = virtualCamera->getHalCamera();

    // Tell the virtual camera's parent to clean it up and drop it
    // NOTE:  The camera objects will only actually destruct when the sp<> ref counts get to
    //        zero, so it is important to break all cyclic references.
    halCamera->disownVirtualCamera(virtualCamera);

    // Did we just remove the last client of this camera?
    if (halCamera->getClientCount() == 0) {
        // Take this now unused camera out of our list
        // NOTE:  This should drop our last reference to the camera, resulting in its
        //        destruction.
        mCameras.remove(halCamera);
    }

    return Void();
}


Return<sp<IEvsDisplay>> Enumerator::openDisplay() {
    ALOGD("openDisplay");

    if (!checkPermission()) {
        return nullptr;
    }

    // We simply keep track of the most recently opened display instance.
    // In the underlying layers we expect that a new open will cause the previous
    // object to be destroyed.  This avoids any race conditions associated with
    // create/destroy order and provides a cleaner restart sequence if the previous owner
    // is non-responsive for some reason.
    // Request exclusive access to the EVS display
    sp<IEvsDisplay> pActiveDisplay = mHwEnumerator->openDisplay();
    if (pActiveDisplay == nullptr) {
        ALOGE("EVS Display unavailable");

        return nullptr;
    }

    // Remember (via weak pointer) who we think the most recently opened display is so that
    // we can proxy state requests from other callers to it.
    // TODO: Because of b/129284474, an additional class, HalDisplay, has been defined and
    // wraps the IEvsDisplay object the driver returns.  We may want to remove this
    // additional class when it is fixed properly.
    sp<IEvsDisplay> pHalDisplay = new HalDisplay(pActiveDisplay);
    mActiveDisplay = pHalDisplay;

    return pHalDisplay;
}


Return<void> Enumerator::closeDisplay(const ::android::sp<IEvsDisplay>& display) {
    ALOGD("closeDisplay");

    sp<IEvsDisplay> pActiveDisplay = mActiveDisplay.promote();

    // Drop the active display
    if (display.get() != pActiveDisplay.get()) {
        ALOGW("Ignoring call to closeDisplay with unrecognized display object.");
    } else {
        // Pass this request through to the hardware layer
        sp<HalDisplay> halDisplay = reinterpret_cast<HalDisplay *>(pActiveDisplay.get());
        mHwEnumerator->closeDisplay(halDisplay->getHwDisplay());
        mActiveDisplay = nullptr;
    }

    return Void();
}


Return<DisplayState> Enumerator::getDisplayState()  {
    ALOGD("getDisplayState");
    if (!checkPermission()) {
        return DisplayState::DEAD;
    }

    // Do we have a display object we think should be active?
    sp<IEvsDisplay> pActiveDisplay = mActiveDisplay.promote();
    if (pActiveDisplay != nullptr) {
        // Pass this request through to the hardware layer
        return pActiveDisplay->getDisplayState();
    } else {
        // We don't have a live display right now
        mActiveDisplay = nullptr;
        return DisplayState::NOT_OPEN;
    }
}


} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace android
