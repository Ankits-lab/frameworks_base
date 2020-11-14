# Copyright (C) 2014 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SDK_VERSION := 18

LOCAL_PACKAGE_NAME := MultiDexLegacyTestApp_corrupted

LOCAL_STATIC_JAVA_LIBRARIES := android-support-multidex

LOCAL_DEX_PREOPT := false

include $(BUILD_PACKAGE)

corrupted_classes2_dex := $(dir $(built_dex))/classes2.dex

$(corrupted_classes2_dex): $(built_dex)
	$(hide) touch $@

$(LOCAL_BUILT_MODULE): $(corrupted_classes2_dex)
