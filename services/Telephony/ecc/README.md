This directory contains tools and data related to ECC (Emergency Call Codes)
data and updates.

Directory structure
===================

gen_eccdata.sh
  - A script to launch the newest conversion toolset to convert
    input/eccdata.txt into output/eccdata.

input/eccdata.txt
  - A text file in ProtoBuf text format which contains all known ECC data.

output/eccdata
  - The binary file generated from input files.

conversion_toolset_v*
  - Contains format definitions and converting tools.

proto
  - A symbolic link references to protobuf folder of the newest version of
    conversion toolsets. It's used in Android.mk.

Updating ECC database
===================
Steps to update the ECC database:
1. Edit input/eccdata.txt
2. Source and launch
3. Run gen_eccdata.sh
4. Make TeleService
5. Push TeleService.apk to system/priv-app/TeleService
6. Reboot device
7. run 'atest TeleServiceTests:EccDataTest#testEccDataContent'
