/*
**
** Copyright 2014, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package android.app.trust;

import android.app.trust.ITrustListener;
import android.hardware.biometrics.BiometricSourceType;

/**
 * System private API to comunicate with trust service.
 *
 * {@hide}
 */
interface ITrustManager {
    void reportUnlockAttempt(boolean successful, int userId);
    void reportUnlockLockout(int timeoutMs, int userId);
    void reportEnabledTrustAgentsChanged(int userId);
    void registerTrustListener(in ITrustListener trustListener);
    void unregisterTrustListener(in ITrustListener trustListener);
    void reportKeyguardShowingChanged();
    void setDeviceLockedForUser(int userId, boolean locked);
    boolean isDeviceLocked(int userId);
    boolean isDeviceSecure(int userId);
    boolean isTrustUsuallyManaged(int userId);
    void unlockedByBiometricForUser(int userId, in BiometricSourceType source);
    void clearAllBiometricRecognized(in BiometricSourceType target);
}
