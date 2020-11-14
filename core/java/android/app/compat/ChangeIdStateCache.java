/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.compat;

import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.compat.IPlatformCompat;

/**
 * Handles caching of calls to {@link com.android.internal.compat.IPlatformCompat}
 * @hide
 */
public final class ChangeIdStateCache
        extends PropertyInvalidatedCache<ChangeIdStateQuery, Boolean> {
    private static final String CACHE_KEY = "cache_key.is_compat_change_enabled";
    private static final int MAX_ENTRIES = 20;
    private static boolean sDisabled = false;

    /** @hide */
    public ChangeIdStateCache() {
        super(MAX_ENTRIES, CACHE_KEY);
    }

    /**
     * Disable cache.
     *
     * <p>Should only be used in unit tests.
     * @hide
     */
    public static void disable() {
        sDisabled = true;
    }

    /**
     * Invalidate the cache.
     *
     * <p>Can only be called by the system server process.
     * @hide
     */
    public static void invalidate() {
        if (!sDisabled) {
            PropertyInvalidatedCache.invalidateCache(CACHE_KEY);
        }
    }

    @Override
    protected Boolean recompute(ChangeIdStateQuery query) {
        IPlatformCompat platformCompat = IPlatformCompat.Stub.asInterface(
                ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE));
        final long token = Binder.clearCallingIdentity();
        try {
            if (query.type == ChangeIdStateQuery.QUERY_BY_PACKAGE_NAME) {
                return platformCompat.isChangeEnabledByPackageName(query.changeId,
                                                                   query.packageName,
                                                                   query.userId);
            } else if (query.type == ChangeIdStateQuery.QUERY_BY_UID) {
                return platformCompat.isChangeEnabledByUid(query.changeId, query.uid);
            } else {
                throw new IllegalArgumentException("Invalid query type: " + query.type);
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        throw new IllegalStateException("Could not recompute value!");
    }
}
