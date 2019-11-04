LOCAL_MODULE_TAGS := optional
LOCAL_PRIVILEGED_MODULE := true

LOCAL_STATIC_JAVA_LIBRARIES += guava \
        apache-commons-compress \
        docsui-statsd

LOCAL_STATIC_ANDROID_LIBRARIES := \
        androidx.legacy_legacy-support-core-ui \
        androidx.legacy_legacy-support-v4 \
        androidx.appcompat_appcompat \
        androidx.legacy_legacy-support-v13 \
        androidx.transition_transition \
        androidx.recyclerview_recyclerview \
        androidx.recyclerview_recyclerview-selection \
        com.google.android.material_material

LOCAL_USE_AAPT2 := true

LOCAL_JACK_FLAGS := \
  -D jack.optimization.inner-class.accessors=true

# Only enable asserts on userdebug/eng builds
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
LOCAL_JACK_FLAGS += -D jack.assert.policy=always
endif

LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# TODO: build against public API as part of b/110959821
LOCAL_PRIVATE_PLATFORM_APIS := true
#LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
