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
package com.android.server.notification;

import static android.os.UserHandle.USER_CURRENT;
import static android.os.UserHandle.USER_SYSTEM;
import static android.service.notification.NotificationListenerService.REASON_CANCEL;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.UiServiceTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ArchiveTest extends UiServiceTestCase {
    private static final int SIZE = 5;

    private NotificationManagerService.Archive mArchive;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mArchive = new NotificationManagerService.Archive(SIZE);
        mArchive.updateHistoryEnabled(USER_SYSTEM, true);
        mArchive.updateHistoryEnabled(USER_CURRENT, true);
    }

    private StatusBarNotification getNotification(String pkg, int id, UserHandle user) {
        Notification n = new Notification.Builder(getContext(), "test")
                .setContentTitle("A")
                .setWhen(1205)
                .build();
        return  new StatusBarNotification(
                pkg, pkg, id, null, 0, 0, n, user, null, System.currentTimeMillis());
    }


    @Test
    public void testRecordAndRead() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            StatusBarNotification sbn = getNotification("pkg" + i, i,
                    UserHandle.of(i % 2 ==0 ? USER_SYSTEM : USER_CURRENT));
            expected.add(sbn.getKey());
            mArchive.record(sbn, REASON_CANCEL);
        }

        List<StatusBarNotification> actual = Arrays.asList(mArchive.getArray(SIZE, true));
        assertThat(actual).hasSize(expected.size());
        for (StatusBarNotification sbn : actual) {
            assertThat(expected).contains(sbn.getKey());
        }
    }

    @Test
    public void testRecordAndRead_overLimit() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < (SIZE * 2); i++) {
            StatusBarNotification sbn = getNotification("pkg" + i, i, UserHandle.of(USER_SYSTEM));
            mArchive.record(sbn, REASON_CANCEL);
            if (i >= SIZE) {
                expected.add(sbn.getKey());
            }
        }

        List<StatusBarNotification> actual = Arrays.asList(mArchive.getArray((SIZE * 2), true));
        assertThat(actual).hasSize(expected.size());
        for (StatusBarNotification sbn : actual) {
            assertThat(expected).contains(sbn.getKey());
        }
    }

    @Test
    public void testDoesNotRecordIfHistoryDisabled() {
        mArchive.updateHistoryEnabled(USER_CURRENT, false);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            StatusBarNotification sbn = getNotification("pkg" + i, i,
                    UserHandle.of(i % 2 ==0 ? USER_SYSTEM : USER_CURRENT));
            mArchive.record(sbn, REASON_CANCEL);
            if (i % 2 ==0) {
                expected.add(sbn.getKey());
            }
        }

        List<StatusBarNotification> actual = Arrays.asList(mArchive.getArray(SIZE, true));
        assertThat(actual).hasSize(expected.size());
        for (StatusBarNotification sbn : actual) {
            assertThat(expected).contains(sbn.getKey());
        }
    }

    @Test
    public void testRemovesEntriesWhenHistoryDisabled() {
        mArchive.updateHistoryEnabled(USER_CURRENT, true);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            StatusBarNotification sbn = getNotification("pkg" + i, i,
                    UserHandle.of(i % 2 ==0 ? USER_SYSTEM : USER_CURRENT));
            mArchive.record(sbn, REASON_CANCEL);
            if (i % 2 ==0) {
                expected.add(sbn.getKey());
            }
        }
        mArchive.updateHistoryEnabled(USER_CURRENT, false);

        List<StatusBarNotification> actual = Arrays.asList(mArchive.getArray(SIZE, true));
        assertThat(actual).hasSize(expected.size());
        for (StatusBarNotification sbn : actual) {
            assertThat(expected).contains(sbn.getKey());
        }
    }
}
