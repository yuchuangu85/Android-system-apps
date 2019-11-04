#
# Include this make file to build your application with car ui.
# This only applied to app which is not CarActivity based but wants to use car-ui.
#
# Make sure to include it after you've set all your desired LOCAL variables.
# Note that you must explicitly set your LOCAL_RESOURCE_DIR before including this file.
#
# For example:
#
#   LOCAL_RESOURCE_DIR := \
#        $(LOCAL_PATH)/res
#
#   In your .mk file, include the items in the following order, to ensure the prebuilt
#   static libraries are included in the correct order.
#
#   include vendor/auto/embedded/prebuilts/android-car-lib/car-lib.mk
#   include $(BUILD_PACKAGE)
#   include vendor/auto/embedded/prebuilts/android-car-lib/Android.mk

# Check that LOCAL_RESOURCE_DIR is defined
ifeq (,$(LOCAL_RESOURCE_DIR))
$(error LOCAL_RESOURCE_DIR must be defined)
endif

LOCAL_STATIC_JAVA_AAR_LIBRARIES += android-car

# Work around limitations of AAR prebuilts
LOCAL_RESOURCE_DIR += packages/apps/Car/libs/android-car-lib/res

# Include support-v7-appcompat, if not already included
ifeq (,$(findstring android-support-v7-appcompat,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v7-appcompat
endif

# Include support-v7-recyclerview, if not already included
ifeq (,$(findstring android-support-v7-recyclerview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v7-recyclerview
endif

# Include support-v7-cardview, if not already included
ifeq (,$(findstring android-support-v7-cardview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v7-cardview
endif

# Include support-design, if not already included
ifeq (,$(findstring android-support-design,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_ANDROID_LIBRARIES += android-support-design
endif

# Include support-v4, if not already included
ifeq (,$(findstring android-support-v4,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v4
endif
