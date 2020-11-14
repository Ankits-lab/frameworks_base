/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.projection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;

/**
 * Manages the retrieval of certain types of {@link MediaProjection} tokens.
 */
@SystemService(Context.MEDIA_PROJECTION_SERVICE)
public final class MediaProjectionManager {
    private static final String TAG = "MediaProjectionManager";
    /** @hide */
    public static final String EXTRA_APP_TOKEN = "android.media.projection.extra.EXTRA_APP_TOKEN";
    /** @hide */
    public static final String EXTRA_MEDIA_PROJECTION =
            "android.media.projection.extra.EXTRA_MEDIA_PROJECTION";

    /** @hide */
    public static final int TYPE_SCREEN_CAPTURE = 0;
    /** @hide */
    public static final int TYPE_MIRRORING = 1;
    /** @hide */
    public static final int TYPE_PRESENTATION = 2;

    private Context mContext;
    private Map<Callback, CallbackDelegate> mCallbacks;
    private IMediaProjectionManager mService;

    /** @hide */
    public MediaProjectionManager(Context context) {
        mContext = context;
        IBinder b = ServiceManager.getService(Context.MEDIA_PROJECTION_SERVICE);
        mService = IMediaProjectionManager.Stub.asInterface(b);
        mCallbacks = new ArrayMap<>();
    }

    /**
     * Returns an Intent that <b>must</b> be passed to startActivityForResult()
     * in order to start screen capture. The activity will prompt
     * the user whether to allow screen capture.  The result of this
     * activity should be passed to getMediaProjection.
     */
    public Intent createScreenCaptureIntent() {
        Intent i = new Intent();
        final ComponentName mediaProjectionPermissionDialogComponent =
                ComponentName.unflattenFromString(mContext.getResources().getString(
                        com.android.internal.R.string
                        .config_mediaProjectionPermissionDialogComponent));
        i.setComponent(mediaProjectionPermissionDialogComponent);
        return i;
    }

    /**
     * Retrieve the MediaProjection obtained from a succesful screen
     * capture request. Will be null if the result from the
     * startActivityForResult() is anything other than RESULT_OK.
     *
     * Starting from Android {@link android.os.Build.VERSION_CODES#R}, if your application requests
     * the {@link android.Manifest.permission#SYSTEM_ALERT_WINDOW} permission, and the
     * user has not explicitly denied it, the permission will be automatically granted until the
     * projection is stopped. This allows for user controls to be displayed on top of the screen
     * being captured.
     *
     * <p>
     * Apps targeting SDK version {@link android.os.Build.VERSION_CODES#Q} or later should specify
     * the foreground service type using the attribute {@link android.R.attr#foregroundServiceType}
     * in the service element of the app's manifest file.
     * The {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION} attribute
     * should be specified.
     * </p>
     *
     * @see <a href="https://developer.android.com/preview/privacy/foreground-service-types">
     * Foregroud Service Types</a>
     *
     * @param resultCode The result code from {@link android.app.Activity#onActivityResult(int,
     * int, android.content.Intent)}
     * @param resultData The resulting data from {@link android.app.Activity#onActivityResult(int,
     * int, android.content.Intent)}
     * @throws IllegalStateException on pre-Q devices if a previously gotten MediaProjection
     * from the same {@code resultData} has not yet been stopped
     */
    public MediaProjection getMediaProjection(int resultCode, @NonNull Intent resultData) {
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            return null;
        }
        IBinder projection = resultData.getIBinderExtra(EXTRA_MEDIA_PROJECTION);
        if (projection == null) {
            return null;
        }
        return new MediaProjection(mContext, IMediaProjection.Stub.asInterface(projection));
    }

    /**
     * Get the {@link MediaProjectionInfo} for the active {@link MediaProjection}.
     * @hide
     */
    public MediaProjectionInfo getActiveProjectionInfo() {
        try {
            return mService.getActiveProjectionInfo();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get the active projection info", e);
        }
        return null;
    }

    /**
     * Stop the current projection if there is one.
     * @hide
     */
    public void stopActiveProjection() {
        try {
            mService.stopActiveProjection();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to stop the currently active media projection", e);
        }
    }

    /**
     * Add a callback to monitor all of the {@link MediaProjection}s activity.
     * Not for use by regular applications, must have the MANAGE_MEDIA_PROJECTION permission.
     * @hide
     */
    public void addCallback(@NonNull Callback callback, @Nullable Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        CallbackDelegate delegate = new CallbackDelegate(callback, handler);
        mCallbacks.put(callback, delegate);
        try {
            mService.addCallback(delegate);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to add callbacks to MediaProjection service", e);
        }
    }

    /**
     * Remove a MediaProjection monitoring callback.
     * @hide
     */
    public void removeCallback(@NonNull Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        CallbackDelegate delegate = mCallbacks.remove(callback);
        try {
            if (delegate != null) {
                mService.removeCallback(delegate);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to add callbacks to MediaProjection service", e);
        }
    }

    /** @hide */
    public static abstract class Callback {
        public abstract void onStart(MediaProjectionInfo info);
        public abstract void onStop(MediaProjectionInfo info);
    }

    /** @hide */
    private final static class CallbackDelegate extends IMediaProjectionWatcherCallback.Stub {
        private Callback mCallback;
        private Handler mHandler;

        public CallbackDelegate(Callback callback, Handler handler) {
            mCallback = callback;
            if (handler == null) {
                handler = new Handler();
            }
            mHandler = handler;
        }

        @Override
        public void onStart(final MediaProjectionInfo info) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStart(info);
                }
            });
        }

        @Override
        public void onStop(final MediaProjectionInfo info) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStop(info);
                }
            });
        }
    }
}
