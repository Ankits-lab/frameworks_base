/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.res;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.res.loader.ResourcesLoader;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Objects;

/** @hide */
public final class ResourcesKey {
    @Nullable
    @UnsupportedAppUsage
    public final String mResDir;

    @Nullable
    @UnsupportedAppUsage
    public final String[] mSplitResDirs;

    @Nullable
    public final String[] mOverlayDirs;

    @Nullable
    public final String[] mLibDirs;

    public final int mDisplayId;

    @NonNull
    public final Configuration mOverrideConfiguration;

    @NonNull
    public final CompatibilityInfo mCompatInfo;

    @Nullable
    public final ResourcesLoader[] mLoaders;

    private final int mHash;

    public ResourcesKey(@Nullable String resDir,
                        @Nullable String[] splitResDirs,
                        @Nullable String[] overlayDirs,
                        @Nullable String[] libDirs,
                        int displayId,
                        @Nullable Configuration overrideConfig,
                        @Nullable CompatibilityInfo compatInfo,
                        @Nullable ResourcesLoader[] loader) {
        mResDir = resDir;
        mSplitResDirs = splitResDirs;
        mOverlayDirs = overlayDirs;
        mLibDirs = libDirs;
        mLoaders = (loader != null && loader.length == 0) ? null : loader;
        mDisplayId = displayId;
        mOverrideConfiguration = new Configuration(overrideConfig != null
                ? overrideConfig : Configuration.EMPTY);
        mCompatInfo = compatInfo != null ? compatInfo : CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO;

        int hash = 17;
        hash = 31 * hash + Objects.hashCode(mResDir);
        hash = 31 * hash + Arrays.hashCode(mSplitResDirs);
        hash = 31 * hash + Arrays.hashCode(mOverlayDirs);
        hash = 31 * hash + Arrays.hashCode(mLibDirs);
        hash = 31 * hash + mDisplayId;
        hash = 31 * hash + Objects.hashCode(mOverrideConfiguration);
        hash = 31 * hash + Objects.hashCode(mCompatInfo);
        hash = 31 * hash + Arrays.hashCode(mLoaders);
        mHash = hash;
    }

    @UnsupportedAppUsage
    public ResourcesKey(@Nullable String resDir,
            @Nullable String[] splitResDirs,
            @Nullable String[] overlayDirs,
            @Nullable String[] libDirs,
            int displayId,
            @Nullable Configuration overrideConfig,
            @Nullable CompatibilityInfo compatInfo) {
        this(resDir, splitResDirs, overlayDirs, libDirs, displayId, overrideConfig, compatInfo,
                null);
    }

    public boolean hasOverrideConfiguration() {
        return !Configuration.EMPTY.equals(mOverrideConfiguration);
    }

    public boolean isPathReferenced(String path) {
        if (mResDir != null && mResDir.startsWith(path)) {
            return true;
        } else {
            return anyStartsWith(mSplitResDirs, path) || anyStartsWith(mOverlayDirs, path)
                    || anyStartsWith(mLibDirs, path);
        }
    }

    private static boolean anyStartsWith(String[] list, String prefix) {
        if (list != null) {
            for (String s : list) {
                if (s != null && s.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ResourcesKey)) {
            return false;
        }

        ResourcesKey peer = (ResourcesKey) obj;
        if (mHash != peer.mHash) {
            // If the hashes don't match, the objects can't match.
            return false;
        }

        if (!Objects.equals(mResDir, peer.mResDir)) {
            return false;
        }
        if (!Arrays.equals(mSplitResDirs, peer.mSplitResDirs)) {
            return false;
        }
        if (!Arrays.equals(mOverlayDirs, peer.mOverlayDirs)) {
            return false;
        }
        if (!Arrays.equals(mLibDirs, peer.mLibDirs)) {
            return false;
        }
        if (mDisplayId != peer.mDisplayId) {
            return false;
        }
        if (!Objects.equals(mOverrideConfiguration, peer.mOverrideConfiguration)) {
            return false;
        }
        if (!Objects.equals(mCompatInfo, peer.mCompatInfo)) {
            return false;
        }
        if (!Arrays.equals(mLoaders, peer.mLoaders)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("ResourcesKey{");
        builder.append(" mHash=").append(Integer.toHexString(mHash));
        builder.append(" mResDir=").append(mResDir);
        builder.append(" mSplitDirs=[");
        if (mSplitResDirs != null) {
            builder.append(TextUtils.join(",", mSplitResDirs));
        }
        builder.append("]");
        builder.append(" mOverlayDirs=[");
        if (mOverlayDirs != null) {
            builder.append(TextUtils.join(",", mOverlayDirs));
        }
        builder.append("]");
        builder.append(" mLibDirs=[");
        if (mLibDirs != null) {
            builder.append(TextUtils.join(",", mLibDirs));
        }
        builder.append("]");
        builder.append(" mDisplayId=").append(mDisplayId);
        builder.append(" mOverrideConfig=").append(Configuration.resourceQualifierString(
                mOverrideConfiguration));
        builder.append(" mCompatInfo=").append(mCompatInfo);
        builder.append(" mLoaders=[");
        if (mLoaders != null) {
            builder.append(TextUtils.join(",", mLoaders));
        }
        builder.append("]}");
        return builder.toString();
    }
}
