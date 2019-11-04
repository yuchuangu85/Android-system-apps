#!/bin/bash
set -o errexit

# Copyright 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [ -z "${ANDROID_BUILD_TOP}" ] ; then
  echo "You need to source and lunch before you can use this script" 1>&2
  exit 1
fi

case $(uname -s) in
  Darwin)
    KERNEL=darwin
    ;;
  Linux)
    KERNEL=linux
    ;;
  *)
    echo "Unknown kernel \"`uname -s`\"" 1>&2
    exit 1
    ;;
esac

read -d "" PROTOC_COMMAND << END || :
${ANDROID_BUILD_TOP}/prebuilts/tools/${KERNEL}-x86_64/protoc/bin/protoc
END
if ! [ -x "${PROTOC_COMMAND}" ] ; then
  echo "Missing ${PROTOC_COMMAND}." 1>&2
  exit 1
fi

ECC_ROOT=`realpath \`dirname $0\``
TOOLSET_DIR="${ECC_ROOT}/conversion_toolset_v1"
INPUT_DIR="${ECC_ROOT}/input"
OUTPUT_DIR="${ECC_ROOT}/output"
INTERMEDIATE_DIR="${ECC_ROOT}/.intermediate"

rm -rf "${INTERMEDIATE_DIR}" "${OUTPUT_DIR}/*"
mkdir -p "${INTERMEDIATE_DIR}"

source "${TOOLSET_DIR}/gen_eccdata.sh"
echo

echo "To verify data compatibility:"
echo "  1. make TeleService"
echo "  2. push TeleService.apk to system/priv-app/TeleService"
echo "  3. reboot device"
echo "  4. run 'atest TeleServiceTests:EccDataTest#testEccDataContent'"
