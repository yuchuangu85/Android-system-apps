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

#include <log/log.h>
#include "HalDisplay.h"

namespace android {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {

HalDisplay::HalDisplay(sp<IEvsDisplay>& display) :
  mHwDisplay(display) {
    // nothing to do.
}

HalDisplay::~HalDisplay() {
    shutdown();
}

void HalDisplay::shutdown() {
    // simply release a strong pointer to remote display object.
    mHwDisplay = nullptr;
}

/**
 * Returns a strong pointer to remote display object.
 */
sp<IEvsDisplay> HalDisplay::getHwDisplay() {
    return mHwDisplay;
}

/**
 * Gets basic display information from a hardware display object
 * and returns.
 */
Return<void> HalDisplay::getDisplayInfo(getDisplayInfo_cb _hidl_cb) {
    if (mHwDisplay) {
        mHwDisplay->getDisplayInfo(_hidl_cb);
    }

    return Void();
}

/**
 * Sets the display state as what the clients wants.
 */
Return<EvsResult> HalDisplay::setDisplayState(DisplayState state) {
    if (mHwDisplay) {
        return mHwDisplay->setDisplayState(state);
    } else {
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }
}

/**
 * Gets current display state from a hardware display object and return.
 */
Return<DisplayState> HalDisplay::getDisplayState() {
    if (mHwDisplay) {
        return mHwDisplay->getDisplayState();
    } else {
        return DisplayState::DEAD;
    }
}

/**
 * Returns a handle to a frame buffer associated with the display.
 */
Return<void> HalDisplay::getTargetBuffer(getTargetBuffer_cb _hidl_cb) {
    if (mHwDisplay) {
        mHwDisplay->getTargetBuffer(_hidl_cb);
    }

    return Void();
}

/**
 * Notifies the display that the buffer is ready to be used.
 */
Return<EvsResult> HalDisplay::returnTargetBufferForDisplay(const BufferDesc& buffer) {
    if (mHwDisplay) {
        return mHwDisplay->returnTargetBufferForDisplay(buffer);
    } else {
        return EvsResult::OWNERSHIP_LOST;
    }
}

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace android
