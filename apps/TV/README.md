# Live TV

__Live TV__ is the Open Source reference application for watching TV on Android TVs.

## Source

The source of truth is an internal google repository (aka google3) at
cs/third_party/java_src/android_app/live_channels

Changes are made in the google3 repository and automatically pushed here.

The following files are only in the android repository and must be changed there.

* *.mk
* \*\*/lib/\*.\*

## AOSP instructions

To install LiveTv

```bash
echo "Compiling"
m -j LiveTv
echo  "Installing"
adb install -r ${OUT}/system/priv-app/LiveTv/LiveTv.apk

```

If it is your first time installing LiveTv you will need to do

```bash
adb root
adb remount
adb push ${OUT}/system/priv-app/LiveTv/LiveTv.apk /system/priv-app/LiveTv/LiveTv.apk
adb reboot
```