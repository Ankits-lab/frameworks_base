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

package com.android.systemui.statusbar.notification.row.wrapper;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

/**
 * Wraps a notification containing a decorated custom view.
 */
public class NotificationDecoratedCustomViewWrapper extends NotificationTemplateViewWrapper {

    private View mWrappedView = null;

    protected NotificationDecoratedCustomViewWrapper(Context ctx, View view,
            ExpandableNotificationRow row) {
        super(ctx, view, row);
    }

    @Override
    public void onContentUpdated(ExpandableNotificationRow row) {
        ViewGroup container = mView.findViewById(
                com.android.internal.R.id.notification_main_column);
        Integer childIndex = (Integer) container.getTag(
                com.android.internal.R.id.notification_custom_view_index_tag);
        if (childIndex != null && childIndex != -1) {
            mWrappedView = container.getChildAt(childIndex);
        }
        if (needsInversion(resolveBackgroundColor(), mWrappedView)) {
            invertViewLuminosity(mWrappedView);
        }
        super.onContentUpdated(row);
    }
}
