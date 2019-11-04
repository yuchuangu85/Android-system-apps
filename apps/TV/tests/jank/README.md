# Jank tests for Live Channels


## AOSP instructions

To run the jank tests

```bash
echo "Compiling"
m -j LiveTv TVTestInput TVJankTests
echo  "Installing"
adb install -r ${OUT}/system/priv-app/LiveTv/LiveTv.apk
adb install -r ${OUT}/system/app/TVTestInput/TVTestInput.apk
adb install -r ${OUT}/testcases/TVJankTests/TVJankTests.apk
echo "Setting up test input"
adb shell am instrument \
  -e testSetupMode jank \
  -w com.android.tv.testinput/.instrument.TestSetupInstrumentation
echo "Running the test"
adb shell am instrument \
  -w com.android.tv.tests.jank/android.support.test.runner.AndroidJUnitRunner

```

If it is your first time installing LiveTv you will need to do

```bash
adb root
adb remount
adb push ${OUT}/system/priv-app/LiveTv/LiveTv.apk /system/priv-app/LiveTv/LiveTv.apk
adb reboot
```