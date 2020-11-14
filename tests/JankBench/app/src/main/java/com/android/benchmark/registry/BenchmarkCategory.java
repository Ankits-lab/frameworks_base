/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the
 * License.
 *
 */

package com.android.benchmark.registry;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the category of a particular benchmark.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({BenchmarkCategory.GENERIC, BenchmarkCategory.UI, BenchmarkCategory.COMPUTE})
@interface BenchmarkCategory {
    int GENERIC = 0;
    int UI = 1;
    int COMPUTE = 2;
}
