/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.compat;

import android.annotation.Nullable;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.pm.ApplicationInfo;

import com.android.internal.compat.CompatibilityChangeInfo;
import com.android.server.compat.config.Change;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a single compatibility change.
 *
 * <p>A compatibility change has a default setting, determined by the {@code enableAfterTargetSdk}
 * and {@code disabled} constructor parameters. If a change is {@code disabled}, this overrides any
 * target SDK criteria set. These settings can be overridden for a specific package using
 * {@link #addPackageOverride(String, boolean)}.
 *
 * <p>Note, this class is not thread safe so callers must ensure thread safety.
 */
public final class CompatChange extends CompatibilityChangeInfo {

    /**
     * A change ID to be used only in the CTS test for this SystemApi
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = 1234) // Needs to be > test APK targetSdkVersion.
    private static final long CTS_SYSTEM_API_CHANGEID = 149391281; // This is a bug id.

    /**
     * Callback listener for when compat changes are updated for a package.
     * See {@link #registerListener(ChangeListener)} for more details.
     */
    public interface ChangeListener {
        /**
         * Called upon an override change for packageName and the change this listener is
         * registered for. Called before the app is killed.
         */
        void onCompatChange(String packageName);
    }

    ChangeListener mListener = null;

    private Map<String, Boolean> mPackageOverrides;

    public CompatChange(long changeId) {
        this(changeId, null, -1, false, false, null);
    }

    /**
     * @param changeId Unique ID for the change. See {@link android.compat.Compatibility}.
     * @param name Short descriptive name.
     * @param enableAfterTargetSdk {@code targetSdkVersion} restriction. See {@link EnabledAfter};
     *                             -1 if the change is always enabled.
     * @param disabled If {@code true}, overrides any {@code enableAfterTargetSdk} set.
     */
    public CompatChange(long changeId, @Nullable String name, int enableAfterTargetSdk,
            boolean disabled, boolean loggingOnly, String description) {
        super(changeId, name, enableAfterTargetSdk, disabled, loggingOnly, description);
    }

    /**
     * @param change an object generated by services/core/xsd/platform-compat-config.xsd
     */
    public CompatChange(Change change) {
        super(change.getId(), change.getName(), change.getEnableAfterTargetSdk(),
                change.getDisabled(), change.getLoggingOnly(), change.getDescription());
    }

    void registerListener(ChangeListener listener) {
        if (mListener != null) {
            throw new IllegalStateException(
                    "Listener for change " + toString() + " already registered.");
        }
        mListener = listener;
    }


    /**
     * Force the enabled state of this change for a given package name. The change will only take
     * effect after that packages process is killed and restarted.
     *
     * <p>Note, this method is not thread safe so callers must ensure thread safety.
     *
     * @param pname Package name to enable the change for.
     * @param enabled Whether or not to enable the change.
     */
    void addPackageOverride(String pname, boolean enabled) {
        if (getLoggingOnly()) {
            throw new IllegalArgumentException(
                    "Can't add overrides for a logging only change " + toString());
        }
        if (mPackageOverrides == null) {
            mPackageOverrides = new HashMap<>();
        }
        mPackageOverrides.put(pname, enabled);
        notifyListener(pname);
    }

    /**
     * Remove any package override for the given package name, restoring the default behaviour.
     *
     * <p>Note, this method is not thread safe so callers must ensure thread safety.
     *
     * @param pname Package name to reset to defaults for.
     */
    void removePackageOverride(String pname) {
        if (mPackageOverrides != null) {
            if (mPackageOverrides.remove(pname) != null) {
                notifyListener(pname);
            }
        }
    }

    /**
     * Find if this change is enabled for the given package, taking into account any overrides that
     * exist.
     *
     * @param app Info about the app in question
     * @return {@code true} if the change should be enabled for the package.
     */
    boolean isEnabled(ApplicationInfo app) {
        if (mPackageOverrides != null && mPackageOverrides.containsKey(app.packageName)) {
            return mPackageOverrides.get(app.packageName);
        }
        if (getDisabled()) {
            return false;
        }
        if (getEnableAfterTargetSdk() != -1) {
            return app.targetSdkVersion > getEnableAfterTargetSdk();
        }
        return true;
    }

    /**
     * Checks whether a change has an override for a package.
     * @param packageName name of the package
     * @return true if there is such override
     */
    boolean hasOverride(String packageName) {
        return mPackageOverrides != null && mPackageOverrides.containsKey(packageName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ChangeId(")
                .append(getId());
        if (getName() != null) {
            sb.append("; name=").append(getName());
        }
        if (getEnableAfterTargetSdk() != -1) {
            sb.append("; enableAfterTargetSdk=").append(getEnableAfterTargetSdk());
        }
        if (getDisabled()) {
            sb.append("; disabled");
        }
        if (getLoggingOnly()) {
            sb.append("; loggingOnly");
        }
        if (mPackageOverrides != null && mPackageOverrides.size() > 0) {
            sb.append("; packageOverrides=").append(mPackageOverrides);
        }
        return sb.append(")").toString();
    }

    private void notifyListener(String packageName) {
        if (mListener != null) {
            mListener.onCompatChange(packageName);
        }
    }
}
