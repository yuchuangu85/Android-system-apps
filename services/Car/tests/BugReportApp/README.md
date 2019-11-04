# AAE BugReport App

A test tool that is intended to show how to access Android Automotive OS bugreporting APIs.
It stores all bugreports in user 0 context and then allows uploading them to GCS.

## Flow

1. User long presses Notification icon
2. It opens BugReportActivity as dialog under current user (e.g. u10)
3. BugReportActivity connects to BugReportService and checks if a bugreporting is running.
4. If bugreporting is already running it shows in progress dialog
5. Otherwise it creates MetaBugReport record in a local db and starts recording audio message.
6. When the submit button is clicked, it saves the audio message in temp directory and starts
   BugReportService.
7. If the drivers cancels the dialog, the BugReportActivity deletes temp directory and closes the
   activity.
8. BugReportService running under current user (e.g. u10) starts collecting logs using dumpstate,
    and when finished it updates MetaBugReport using BugStorageProvider.
9. BugStorageProvider is running under u0, it schedules UploadJob.
10. UploadJob runs SimpleUploaderAsyncTask to upload the bugreport.

Bug reports are zipped and uploaded to GCS. GCS enables creating Pub/Sub
notifications that can be used to track when new  bug reports are uploaded.

## System configuration

BugReport app uses `CarBugreportServiceManager` to collect bug reports and
screenshots. `CarBugreportServiceManager` allows only one bug report app to
use it's APIs, by default it's none.

To allow AAE BugReport app to access the API, you need to overlay
`config_car_bugreport_application` in `packages/services/Car/service/res/values/config.xml`
with value `com.google.android.car.bugreport`.

## App Configuration

UI and upload configs are located in `res/` directory. Resources can be
[overlayed](https://source.android.com/setup/develop/new-device#use-resource-overlays)
for specific products.

### System Properties

- `android.car.bugreport.disableautoupload` - set it to `true` to disable auto-upload to Google
   Cloud, and allow users to manually upload or copy the bugreports to flash drive.

### Upload configuration

BugReport app uses `res/raw/gcs_credentials.json` for authentication and
`res/values/configs.xml` for obtaining GCS bucket name.

## Testing

### Manually testing the app using the test script

BugReportApp comes with `utils/bugreport_app_tester.py` script that automates
many of the BugReportApp testing process. Please follow these instructions
to test the app:

1. Connect the device to your computer.
2. Make sure the device has Internet.
3. Run the script: `$ python bugreport_app_tester.py`
   * The script works on python 2.7 and above.
   * If multiple devices connected, see the usage
     `$ python bugreport_app_tester.py --help`.
   * Warning: the script will delete all the bug reports on the device.
4. Script might take up to 10 minutes to finish.
   * It might fail to upload bugreport when time/time-zone is invalid.
   * In rare cases it might not upload the bugreport, depending Android's
     task scheduling rules.
5. Please manually verify the script's results.
6. Please manually verify bug report contents.
   * Images - the should contain screenshots of all the physical displays.
   * Audio files - they should contain the audio message you recorded.
   * Dumpstate (bugreport) - it should contain logs and other information.
