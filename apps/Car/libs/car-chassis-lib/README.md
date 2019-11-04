# Android Automotive 'Chassis' library
Components and resources designed to increase Automotive UI consistency between
GAS (Google Automotive Services) apps, system-apps and OEM developed apps.

See: go/aae-chassis

## Content

Components and resources designed to be configured by means of RRO (Runtime
Resource Overlays) by OEMs.

## Updating

This library is developed in Gerrit and copied as source to Google3 using
Copybara (go/copybara).

Source: /packages/apps/Car/libs/car-chassis-lib
Target: //google3/third_party/java/android_libs/android_car_chassis_lib

Here is the process for updating this library:

1. Develop, test and create CL in Gerrit with the desired changes
2. On Google3, run update.sh and test your changes
3. Iterate until your changes look okay on both places.
4. Back on Gerrit, submit your CL
5. Back on Google3, run update.sh again and submit

TODO: Automate this process using CaaS (in progress)
