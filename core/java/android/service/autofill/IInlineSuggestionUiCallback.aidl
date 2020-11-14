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

package android.service.autofill;

import android.content.IntentSender;
import android.os.IBinder;
import android.service.autofill.IInlineSuggestionUi;
import android.view.SurfaceControlViewHost;

/**
 * Interface to receive events from a remote inline suggestion UI.
 *
 * @hide
 */
oneway interface IInlineSuggestionUiCallback {
    void onClick();
    void onLongClick();
    void onContent(in IInlineSuggestionUi content, in SurfaceControlViewHost.SurfacePackage surface,
                   int width, int height);
    void onError();
    void onTransferTouchFocusToImeWindow(in IBinder sourceInputToken, int displayId);
    void onStartIntentSender(in IntentSender intentSender);
}
