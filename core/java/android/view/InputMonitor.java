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

package android.view;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import com.android.internal.util.DataClass;

/**
 * An {@code InputMonitor} allows privileged applications and components to monitor streams of
 * {@link InputEvent}s without having to be the designated recipient for the event.
 *
 * For example, focus dispatched events would normally only go to the focused window on the
 * targeted display, but an {@code InputMonitor} will also receive a copy of that event if they're
 * registered to monitor that type of event on the targeted display.
 *
 * @hide
 */
@DataClass(genToString = true)
public final class InputMonitor implements Parcelable {
    private static final String TAG = "InputMonitor";

    private static final boolean DEBUG = false;

    @NonNull
    private final InputChannel mInputChannel;
    @NonNull
    private final IInputMonitorHost mHost;


    /**
     * Takes all of the current pointer events streams that are currently being sent to this
     * monitor and generates appropriate cancellations for the windows that would normally get
     * them.
     *
     * This method should be used with caution as unexpected pilfering can break fundamental user
     * interactions.
     */
    public void pilferPointers() {
        try {
            mHost.pilferPointers();
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Disposes the input monitor.
     *
     * Explicitly release all of the resources this monitor is holding on to (e.g. the
     * InputChannel). Once this method is called, this monitor and any resources it's provided may
     * no longer be used.
     */
    public void dispose() {
        mInputChannel.dispose();
        try {
            mHost.dispose();
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }



    // Code below generated by codegen v1.0.7.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/android/view/InputMonitor.java


    @DataClass.Generated.Member
    public InputMonitor(
            @NonNull InputChannel inputChannel,
            @NonNull IInputMonitorHost host) {
        this.mInputChannel = inputChannel;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mInputChannel);
        this.mHost = host;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mHost);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public @NonNull InputChannel getInputChannel() {
        return mInputChannel;
    }

    @DataClass.Generated.Member
    public @NonNull IInputMonitorHost getHost() {
        return mHost;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "InputMonitor { " +
                "inputChannel = " + mInputChannel + ", " +
                "host = " + mHost +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mInputChannel, flags);
        dest.writeStrongInterface(mHost);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ InputMonitor(Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        InputChannel inputChannel = (InputChannel) in.readTypedObject(InputChannel.CREATOR);
        IInputMonitorHost host = IInputMonitorHost.Stub.asInterface(in.readStrongBinder());

        this.mInputChannel = inputChannel;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mInputChannel);
        this.mHost = host;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mHost);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<InputMonitor> CREATOR
            = new Parcelable.Creator<InputMonitor>() {
        @Override
        public InputMonitor[] newArray(int size) {
            return new InputMonitor[size];
        }

        @Override
        public InputMonitor createFromParcel(Parcel in) {
            return new InputMonitor(in);
        }
    };

    @DataClass.Generated(
            time = 1571177265149L,
            codegenVersion = "1.0.7",
            sourceFile = "frameworks/base/core/java/android/view/InputMonitor.java",
            inputSignatures = "private static final  java.lang.String TAG\nprivate static final  boolean DEBUG\nprivate final @android.annotation.NonNull android.view.InputChannel mInputChannel\nprivate final @android.annotation.NonNull android.view.IInputMonitorHost mHost\npublic  void pilferPointers()\npublic  void dispose()\nclass InputMonitor extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genToString=true)")
    @Deprecated
    private void __metadata() {}

}
