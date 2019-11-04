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

LOCAL_TOOLSET_DIR="${ECC_ROOT}/conversion_toolset_v1"
source "${LOCAL_TOOLSET_DIR}/env.sh"

echo "Converting eccdata..."
${ANDROID_BUILD_TOP}/prebuilts/tools/linux-x86_64/protoc/bin/protoc \
  --encode=ecc.AllInfo proto/protobuf_ecc_data.proto \
  < "${INPUT_DATA}" > "${RAW_DATA}"

echo "Compressing eccdata..."
gzip -c < "${RAW_DATA}" > "${OUTPUT_DATA}"

echo "Done"
