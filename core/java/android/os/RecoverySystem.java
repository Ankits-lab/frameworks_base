/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.os;

import static android.view.Display.DEFAULT_DISPLAY;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.app.PendingIntent;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Display;

import libcore.io.Streams;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;

/**
 * RecoverySystem contains methods for interacting with the Android
 * recovery system (the separate partition that can be used to install
 * system updates, wipe user data, etc.)
 */
@SystemService(Context.RECOVERY_SERVICE)
public class RecoverySystem {
    private static final String TAG = "RecoverySystem";

    /**
     * Default location of zip file containing public keys (X509
     * certs) authorized to sign OTA updates.
     */
    private static final File DEFAULT_KEYSTORE =
        new File("/system/etc/security/otacerts.zip");

    /** Send progress to listeners no more often than this (in ms). */
    private static final long PUBLISH_PROGRESS_INTERVAL_MS = 500;

    private static final long DEFAULT_EUICC_FACTORY_RESET_TIMEOUT_MILLIS = 30000L; // 30 s
    private static final long MIN_EUICC_FACTORY_RESET_TIMEOUT_MILLIS = 5000L; // 5 s
    private static final long MAX_EUICC_FACTORY_RESET_TIMEOUT_MILLIS = 60000L; // 60 s

    private static final long DEFAULT_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS =
            45000L; // 45 s
    private static final long MIN_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS = 15000L; // 15 s
    private static final long MAX_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS = 90000L; // 90 s

    /** Used to communicate with recovery.  See bootable/recovery/recovery.cpp. */
    private static final File RECOVERY_DIR = new File("/cache/recovery");
    private static final File LOG_FILE = new File(RECOVERY_DIR, "log");
    private static final String LAST_INSTALL_PATH = "last_install";
    private static final String LAST_PREFIX = "last_";
    private static final String ACTION_EUICC_FACTORY_RESET =
            "com.android.internal.action.EUICC_FACTORY_RESET";
    private static final String ACTION_EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS =
            "com.android.internal.action.EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS";

    /**
     * Used in {@link #wipeEuiccData} & {@link #removeEuiccInvisibleSubs} as package name of
     * callback intent.
     */
    private static final String PACKAGE_NAME_EUICC_DATA_MANAGEMENT_CALLBACK = "android";

    /**
     * The recovery image uses this file to identify the location (i.e. blocks)
     * of an OTA package on the /data partition. The block map file is
     * generated by uncrypt.
     *
     * @hide
     */
    public static final File BLOCK_MAP_FILE = new File(RECOVERY_DIR, "block.map");

    /**
     * UNCRYPT_PACKAGE_FILE stores the filename to be uncrypt'd, which will be
     * read by uncrypt.
     *
     * @hide
     */
    public static final File UNCRYPT_PACKAGE_FILE = new File(RECOVERY_DIR, "uncrypt_file");

    /**
     * UNCRYPT_STATUS_FILE stores the time cost (and error code in the case of a failure)
     * of uncrypt.
     *
     * @hide
     */
    public static final File UNCRYPT_STATUS_FILE = new File(RECOVERY_DIR, "uncrypt_status");

    // Length limits for reading files.
    private static final int LOG_FILE_MAX_LENGTH = 64 * 1024;

    // Prevent concurrent execution of requests.
    private static final Object sRequestLock = new Object();

    private final IRecoverySystem mService;

    /**
     * Interface definition for a callback to be invoked regularly as
     * verification proceeds.
     */
    public interface ProgressListener {
        /**
         * Called periodically as the verification progresses.
         *
         * @param progress  the approximate percentage of the
         *        verification that has been completed, ranging from 0
         *        to 100 (inclusive).
         */
        public void onProgress(int progress);
    }

    /** @return the set of certs that can be used to sign an OTA package. */
    private static HashSet<X509Certificate> getTrustedCerts(File keystore)
        throws IOException, GeneralSecurityException {
        HashSet<X509Certificate> trusted = new HashSet<X509Certificate>();
        if (keystore == null) {
            keystore = DEFAULT_KEYSTORE;
        }
        ZipFile zip = new ZipFile(keystore);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                InputStream is = zip.getInputStream(entry);
                try {
                    trusted.add((X509Certificate) cf.generateCertificate(is));
                } finally {
                    is.close();
                }
            }
        } finally {
            zip.close();
        }
        return trusted;
    }

    /**
     * Verify the cryptographic signature of a system update package
     * before installing it.  Note that the package is also verified
     * separately by the installer once the device is rebooted into
     * the recovery system.  This function will return only if the
     * package was successfully verified; otherwise it will throw an
     * exception.
     *
     * Verification of a package can take significant time, so this
     * function should not be called from a UI thread.  Interrupting
     * the thread while this function is in progress will result in a
     * SecurityException being thrown (and the thread's interrupt flag
     * will be cleared).
     *
     * @param packageFile  the package to be verified
     * @param listener     an object to receive periodic progress
     * updates as verification proceeds.  May be null.
     * @param deviceCertsZipFile  the zip file of certificates whose
     * public keys we will accept.  Verification succeeds if the
     * package is signed by the private key corresponding to any
     * public key in this file.  May be null to use the system default
     * file (currently "/system/etc/security/otacerts.zip").
     *
     * @throws IOException if there were any errors reading the
     * package or certs files.
     * @throws GeneralSecurityException if verification failed
     */
    public static void verifyPackage(File packageFile,
                                     ProgressListener listener,
                                     File deviceCertsZipFile)
        throws IOException, GeneralSecurityException {
        final long fileLen = packageFile.length();

        final RandomAccessFile raf = new RandomAccessFile(packageFile, "r");
        try {
            final long startTimeMillis = System.currentTimeMillis();
            if (listener != null) {
                listener.onProgress(0);
            }

            raf.seek(fileLen - 6);
            byte[] footer = new byte[6];
            raf.readFully(footer);

            if (footer[2] != (byte)0xff || footer[3] != (byte)0xff) {
                throw new SignatureException("no signature in file (no footer)");
            }

            final int commentSize = (footer[4] & 0xff) | ((footer[5] & 0xff) << 8);
            final int signatureStart = (footer[0] & 0xff) | ((footer[1] & 0xff) << 8);

            byte[] eocd = new byte[commentSize + 22];
            raf.seek(fileLen - (commentSize + 22));
            raf.readFully(eocd);

            // Check that we have found the start of the
            // end-of-central-directory record.
            if (eocd[0] != (byte)0x50 || eocd[1] != (byte)0x4b ||
                eocd[2] != (byte)0x05 || eocd[3] != (byte)0x06) {
                throw new SignatureException("no signature in file (bad footer)");
            }

            for (int i = 4; i < eocd.length-3; ++i) {
                if (eocd[i  ] == (byte)0x50 && eocd[i+1] == (byte)0x4b &&
                    eocd[i+2] == (byte)0x05 && eocd[i+3] == (byte)0x06) {
                    throw new SignatureException("EOCD marker found after start of EOCD");
                }
            }

            // Parse the signature
            PKCS7 block =
                new PKCS7(new ByteArrayInputStream(eocd, commentSize+22-signatureStart, signatureStart));

            // Take the first certificate from the signature (packages
            // should contain only one).
            X509Certificate[] certificates = block.getCertificates();
            if (certificates == null || certificates.length == 0) {
                throw new SignatureException("signature contains no certificates");
            }
            X509Certificate cert = certificates[0];
            PublicKey signatureKey = cert.getPublicKey();

            SignerInfo[] signerInfos = block.getSignerInfos();
            if (signerInfos == null || signerInfos.length == 0) {
                throw new SignatureException("signature contains no signedData");
            }
            SignerInfo signerInfo = signerInfos[0];

            // Check that the public key of the certificate contained
            // in the package equals one of our trusted public keys.
            boolean verified = false;
            HashSet<X509Certificate> trusted = getTrustedCerts(
                deviceCertsZipFile == null ? DEFAULT_KEYSTORE : deviceCertsZipFile);
            for (X509Certificate c : trusted) {
                if (c.getPublicKey().equals(signatureKey)) {
                    verified = true;
                    break;
                }
            }
            if (!verified) {
                throw new SignatureException("signature doesn't match any trusted key");
            }

            // The signature cert matches a trusted key.  Now verify that
            // the digest in the cert matches the actual file data.
            raf.seek(0);
            final ProgressListener listenerForInner = listener;
            SignerInfo verifyResult = block.verify(signerInfo, new InputStream() {
                // The signature covers all of the OTA package except the
                // archive comment and its 2-byte length.
                long toRead = fileLen - commentSize - 2;
                long soFar = 0;

                int lastPercent = 0;
                long lastPublishTime = startTimeMillis;

                @Override
                public int read() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (soFar >= toRead) {
                        return -1;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        return -1;
                    }

                    int size = len;
                    if (soFar + size > toRead) {
                        size = (int)(toRead - soFar);
                    }
                    int read = raf.read(b, off, size);
                    soFar += read;

                    if (listenerForInner != null) {
                        long now = System.currentTimeMillis();
                        int p = (int)(soFar * 100 / toRead);
                        if (p > lastPercent &&
                            now - lastPublishTime > PUBLISH_PROGRESS_INTERVAL_MS) {
                            lastPercent = p;
                            lastPublishTime = now;
                            listenerForInner.onProgress(lastPercent);
                        }
                    }

                    return read;
                }
            });

            final boolean interrupted = Thread.interrupted();
            if (listener != null) {
                listener.onProgress(100);
            }

            if (interrupted) {
                throw new SignatureException("verification was interrupted");
            }

            if (verifyResult == null) {
                throw new SignatureException("signature digest verification failed");
            }
        } finally {
            raf.close();
        }

        // Additionally verify the package compatibility.
        if (!readAndVerifyPackageCompatibilityEntry(packageFile)) {
            throw new SignatureException("package compatibility verification failed");
        }
    }

    /**
     * Verifies the compatibility entry from an {@link InputStream}.
     *
     * @return the verification result.
     */
    @UnsupportedAppUsage
    private static boolean verifyPackageCompatibility(InputStream inputStream) throws IOException {
        ArrayList<String> list = new ArrayList<>();
        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            long entrySize = entry.getSize();
            if (entrySize > Integer.MAX_VALUE || entrySize < 0) {
                throw new IOException(
                        "invalid entry size (" + entrySize + ") in the compatibility file");
            }
            byte[] bytes = new byte[(int) entrySize];
            Streams.readFully(zis, bytes);
            list.add(new String(bytes, UTF_8));
        }
        if (list.isEmpty()) {
            throw new IOException("no entries found in the compatibility file");
        }
        return (VintfObject.verify(list.toArray(new String[list.size()])) == 0);
    }

    /**
     * Reads and verifies the compatibility entry in an OTA zip package. The compatibility entry is
     * a zip file (inside the OTA package zip).
     *
     * @return {@code true} if the entry doesn't exist or verification passes.
     */
    private static boolean readAndVerifyPackageCompatibilityEntry(File packageFile)
            throws IOException {
        try (ZipFile zip = new ZipFile(packageFile)) {
            ZipEntry entry = zip.getEntry("compatibility.zip");
            if (entry == null) {
                return true;
            }
            InputStream inputStream = zip.getInputStream(entry);
            return verifyPackageCompatibility(inputStream);
        }
    }

    /**
     * Verifies the package compatibility info against the current system.
     *
     * @param compatibilityFile the {@link File} that contains the package compatibility info.
     * @throws IOException if there were any errors reading the compatibility file.
     * @return the compatibility verification result.
     *
     * {@hide}
     */
    @SystemApi
    @SuppressLint("Doclava125")
    public static boolean verifyPackageCompatibility(File compatibilityFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(compatibilityFile)) {
            return verifyPackageCompatibility(inputStream);
        }
    }

    /**
     * Process a given package with uncrypt. No-op if the package is not on the
     * /data partition.
     *
     * @param Context      the Context to use
     * @param packageFile  the package to be processed
     * @param listener     an object to receive periodic progress updates as
     *                     processing proceeds.  May be null.
     * @param handler      the Handler upon which the callbacks will be
     *                     executed.
     *
     * @throws IOException if there were any errors processing the package file.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void processPackage(Context context,
                                      File packageFile,
                                      final ProgressListener listener,
                                      final Handler handler)
            throws IOException {
        String filename = packageFile.getCanonicalPath();
        if (!filename.startsWith("/data/")) {
            return;
        }

        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        IRecoverySystemProgressListener progressListener = null;
        if (listener != null) {
            final Handler progressHandler;
            if (handler != null) {
                progressHandler = handler;
            } else {
                progressHandler = new Handler(context.getMainLooper());
            }
            progressListener = new IRecoverySystemProgressListener.Stub() {
                int lastProgress = 0;
                long lastPublishTime = System.currentTimeMillis();

                @Override
                public void onProgress(final int progress) {
                    final long now = System.currentTimeMillis();
                    progressHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (progress > lastProgress &&
                                    now - lastPublishTime > PUBLISH_PROGRESS_INTERVAL_MS) {
                                lastProgress = progress;
                                lastPublishTime = now;
                                listener.onProgress(progress);
                            }
                        }
                    });
                }
            };
        }

        if (!rs.uncrypt(filename, progressListener)) {
            throw new IOException("process package failed");
        }
    }

    /**
     * Process a given package with uncrypt. No-op if the package is not on the
     * /data partition.
     *
     * @param Context      the Context to use
     * @param packageFile  the package to be processed
     * @param listener     an object to receive periodic progress updates as
     *                     processing proceeds.  May be null.
     *
     * @throws IOException if there were any errors processing the package file.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void processPackage(Context context,
                                      File packageFile,
                                      final ProgressListener listener)
            throws IOException {
        processPackage(context, packageFile, listener, null);
    }

    /**
     * Reboots the device in order to install the given update
     * package.
     * Requires the {@link android.Manifest.permission#REBOOT} permission.
     *
     * @param context      the Context to use
     * @param packageFile  the update package to install.  Must be on
     * a partition mountable by recovery.  (The set of partitions
     * known to recovery may vary from device to device.  Generally,
     * /cache and /data are safe.)
     *
     * @throws IOException  if writing the recovery command file
     * fails, or if the reboot itself fails.
     */
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void installPackage(Context context, File packageFile)
            throws IOException {
        installPackage(context, packageFile, false);
    }

    /**
     * If the package hasn't been processed (i.e. uncrypt'd), set up
     * UNCRYPT_PACKAGE_FILE and delete BLOCK_MAP_FILE to trigger uncrypt during the
     * reboot.
     *
     * @param context      the Context to use
     * @param packageFile  the update package to install.  Must be on a
     * partition mountable by recovery.
     * @param processed    if the package has been processed (uncrypt'd).
     *
     * @throws IOException if writing the recovery command file fails, or if
     * the reboot itself fails.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void installPackage(Context context, File packageFile, boolean processed)
            throws IOException {
        synchronized (sRequestLock) {
            LOG_FILE.delete();
            // Must delete the file in case it was created by system server.
            UNCRYPT_PACKAGE_FILE.delete();

            String filename = packageFile.getCanonicalPath();
            Log.w(TAG, "!!! REBOOTING TO INSTALL " + filename + " !!!");

            // If the package name ends with "_s.zip", it's a security update.
            boolean securityUpdate = filename.endsWith("_s.zip");

            // If the package is on the /data partition, the package needs to
            // be processed (i.e. uncrypt'd). The caller specifies if that has
            // been done in 'processed' parameter.
            if (filename.startsWith("/data/")) {
                if (processed) {
                    if (!BLOCK_MAP_FILE.exists()) {
                        Log.e(TAG, "Package claimed to have been processed but failed to find "
                                + "the block map file.");
                        throw new IOException("Failed to find block map file");
                    }
                } else {
                    FileWriter uncryptFile = new FileWriter(UNCRYPT_PACKAGE_FILE);
                    try {
                        uncryptFile.write(filename + "\n");
                    } finally {
                        uncryptFile.close();
                    }
                    // UNCRYPT_PACKAGE_FILE needs to be readable and writable
                    // by system server.
                    if (!UNCRYPT_PACKAGE_FILE.setReadable(true, false)
                            || !UNCRYPT_PACKAGE_FILE.setWritable(true, false)) {
                        Log.e(TAG, "Error setting permission for " + UNCRYPT_PACKAGE_FILE);
                    }

                    BLOCK_MAP_FILE.delete();
                }

                // If the package is on the /data partition, use the block map
                // file as the package name instead.
                filename = "@/cache/recovery/block.map";
            }

            final String filenameArg = "--update_package=" + filename + "\n";
            final String localeArg = "--locale=" + Locale.getDefault().toLanguageTag() + "\n";
            final String securityArg = "--security\n";

            String command = filenameArg + localeArg;
            if (securityUpdate) {
                command += securityArg;
            }

            RecoverySystem rs = (RecoverySystem) context.getSystemService(
                    Context.RECOVERY_SERVICE);
            if (!rs.setupBcb(command)) {
                throw new IOException("Setup BCB failed");
            }

            // Having set up the BCB (bootloader control block), go ahead and reboot
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            String reason = PowerManager.REBOOT_RECOVERY_UPDATE;

            // On TV, reboot quiescently if the screen is off
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                DisplayManager dm = context.getSystemService(DisplayManager.class);
                if (dm.getDisplay(DEFAULT_DISPLAY).getState() != Display.STATE_ON) {
                    reason += ",quiescent";
                }
            }
            pm.reboot(reason);

            throw new IOException("Reboot failed (no permissions?)");
        }
    }

    /**
     * Prepare to apply an unattended update by asking the user for their Lock Screen Knowledge
     * Factor (LSKF). If supplied, the {@code intentSender} will be called when the system is setup
     * and ready to apply the OTA.
     * <p>
     * When the system is already prepared for update and this API is called again with the same
     * {@code updateToken}, it will not call the intent sender nor request the user enter their Lock
     * Screen Knowledge Factor.
     * <p>
     * When this API is called again with a different {@code updateToken}, the prepared-for-update
     * status is reset and process repeats as though it's the initial call to this method as
     * described in the first paragraph.
     *
     * @param context the Context to use.
     * @param updateToken token used to indicate which update was prepared
     * @param intentSender the intent to call when the update is prepared; may be {@code null}
     * @throws IOException if there were any errors setting up unattended update
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void prepareForUnattendedUpdate(@NonNull Context context,
            @NonNull String updateToken, @Nullable IntentSender intentSender) throws IOException {
        if (updateToken == null) {
            throw new NullPointerException("updateToken == null");
        }
        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        if (!rs.requestLskf(updateToken, intentSender)) {
            throw new IOException("preparation for update failed");
        }
    }

    /**
     * Request that any previously requested Lock Screen Knowledge Factor (LSKF) is cleared and
     * the preparation for unattended update is reset.
     *
     * @param context the Context to use.
     * @throws IOException if there were any errors clearing the unattended update state
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void clearPrepareForUnattendedUpdate(@NonNull Context context)
            throws IOException {
        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        if (!rs.clearLskf()) {
            throw new IOException("could not reset unattended update state");
        }
    }

    /**
     * Request that the device reboot and apply the update that has been prepared. The
     * {@code updateToken} must match what was given for {@link #prepareForUnattendedUpdate} or
     * this will return {@code false}.
     *
     * @param context the Context to use.
     * @param updateToken the token used to call {@link #prepareForUnattendedUpdate} before
     * @param reason the reboot reason to give to the {@link PowerManager}
     * @throws IOException if the reboot couldn't proceed because the device wasn't ready for an
     *               unattended reboot or if the {@code updateToken} did not match the previously
     *               given token
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void rebootAndApply(@NonNull Context context, @NonNull String updateToken,
            @NonNull String reason) throws IOException {
        if (updateToken == null) {
            throw new NullPointerException("updateToken == null");
        }
        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        if (!rs.rebootWithLskf(updateToken, reason)) {
            throw new IOException("system not prepared to apply update");
        }
    }

    /**
     * Schedule to install the given package on next boot. The caller needs to ensure that the
     * package must have been processed (uncrypt'd) if needed. It sets up the command in BCB
     * (bootloader control block), which will be read by the bootloader and the recovery image.
     *
     * @param context the Context to use.
     * @param packageFile the package to be installed.
     * @throws IOException if there were any errors setting up the BCB.
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void scheduleUpdateOnBoot(Context context, File packageFile) throws IOException {
        String filename = packageFile.getCanonicalPath();
        boolean securityUpdate = filename.endsWith("_s.zip");

        // If the package is on the /data partition, use the block map file as
        // the package name instead.
        if (filename.startsWith("/data/")) {
            filename = "@/cache/recovery/block.map";
        }

        final String filenameArg = "--update_package=" + filename + "\n";
        final String localeArg = "--locale=" + Locale.getDefault().toLanguageTag() + "\n";
        final String securityArg = "--security\n";

        String command = filenameArg + localeArg;
        if (securityUpdate) {
            command += securityArg;
        }

        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        if (!rs.setupBcb(command)) {
            throw new IOException("schedule update on boot failed");
        }
    }

    /**
     * Cancel any scheduled update by clearing up the BCB (bootloader control
     * block).
     *
     * @param Context      the Context to use.
     *
     * @throws IOException if there were any errors clearing up the BCB.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.RECOVERY)
    public static void cancelScheduledUpdate(Context context)
            throws IOException {
        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        if (!rs.clearBcb()) {
            throw new IOException("cancel scheduled update failed");
        }
    }

    /**
     * Reboots the device and wipes the user data and cache
     * partitions.  This is sometimes called a "factory reset", which
     * is something of a misnomer because the system partition is not
     * restored to its factory state.  Requires the
     * {@link android.Manifest.permission#REBOOT} permission.
     *
     * @param context  the Context to use
     *
     * @throws IOException  if writing the recovery command file
     * fails, or if the reboot itself fails.
     * @throws SecurityException if the current user is not allowed to wipe data.
     */
    public static void rebootWipeUserData(Context context) throws IOException {
        rebootWipeUserData(context, false /* shutdown */, context.getPackageName(),
                false /* force */, false /* wipeEuicc */);
    }

    /** {@hide} */
    public static void rebootWipeUserData(Context context, String reason) throws IOException {
        rebootWipeUserData(context, false /* shutdown */, reason, false /* force */,
                false /* wipeEuicc */);
    }

    /** {@hide} */
    public static void rebootWipeUserData(Context context, boolean shutdown)
            throws IOException {
        rebootWipeUserData(context, shutdown, context.getPackageName(), false /* force */,
                false /* wipeEuicc */);
    }

    /** {@hide} */
    public static void rebootWipeUserData(Context context, boolean shutdown, String reason,
            boolean force) throws IOException {
        rebootWipeUserData(context, shutdown, reason, force, false /* wipeEuicc */);
    }

    /**
     * Reboots the device and wipes the user data and cache
     * partitions.  This is sometimes called a "factory reset", which
     * is something of a misnomer because the system partition is not
     * restored to its factory state.  Requires the
     * {@link android.Manifest.permission#REBOOT} permission.
     *
     * @param context   the Context to use
     * @param shutdown  if true, the device will be powered down after
     *                  the wipe completes, rather than being rebooted
     *                  back to the regular system.
     * @param reason    the reason for the wipe that is visible in the logs
     * @param force     whether the {@link UserManager.DISALLOW_FACTORY_RESET} user restriction
     *                  should be ignored
     * @param wipeEuicc whether wipe the euicc data
     *
     * @throws IOException  if writing the recovery command file
     * fails, or if the reboot itself fails.
     * @throws SecurityException if the current user is not allowed to wipe data.
     *
     * @hide
     */
    public static void rebootWipeUserData(Context context, boolean shutdown, String reason,
            boolean force, boolean wipeEuicc) throws IOException {
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (!force && um.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET)) {
            throw new SecurityException("Wiping data is not allowed for this user.");
        }
        final ConditionVariable condition = new ConditionVariable();

        Intent intent = new Intent("android.intent.action.MASTER_CLEAR_NOTIFICATION");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        context.sendOrderedBroadcastAsUser(intent, UserHandle.SYSTEM,
                android.Manifest.permission.MASTER_CLEAR,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        condition.open();
                    }
                }, null, 0, null, null);

        // Block until the ordered broadcast has completed.
        condition.block();

        EuiccManager euiccManager = context.getSystemService(EuiccManager.class);
        if (wipeEuicc) {
            wipeEuiccData(context, PACKAGE_NAME_EUICC_DATA_MANAGEMENT_CALLBACK);
        } else {
            removeEuiccInvisibleSubs(context, euiccManager);
        }

        String shutdownArg = null;
        if (shutdown) {
            shutdownArg = "--shutdown_after";
        }

        String reasonArg = null;
        if (!TextUtils.isEmpty(reason)) {
            String timeStamp = DateFormat.format("yyyy-MM-ddTHH:mm:ssZ", System.currentTimeMillis()).toString();
            reasonArg = "--reason=" + sanitizeArg(reason + "," + timeStamp);
        }

        final String localeArg = "--locale=" + Locale.getDefault().toLanguageTag() ;
        bootCommand(context, shutdownArg, "--wipe_data", reasonArg, localeArg);
    }

    /**
     * Returns whether wipe Euicc data successfully or not.
     *
     * @param packageName the package name of the caller app.
     *
     * @hide
     */
    public static boolean wipeEuiccData(Context context, final String packageName) {
        ContentResolver cr = context.getContentResolver();
        if (Settings.Global.getInt(cr, Settings.Global.EUICC_PROVISIONED, 0) == 0) {
            // If the eUICC isn't provisioned, there's no reason to either wipe or retain profiles,
            // as there's nothing to wipe nor retain.
            Log.d(TAG, "Skipping eUICC wipe/retain as it is not provisioned");
            return true;
        }

        EuiccManager euiccManager = (EuiccManager) context.getSystemService(
                Context.EUICC_SERVICE);
        if (euiccManager != null && euiccManager.isEnabled()) {
            CountDownLatch euiccFactoryResetLatch = new CountDownLatch(1);
            final AtomicBoolean wipingSucceeded = new AtomicBoolean(false);

            BroadcastReceiver euiccWipeFinishReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_EUICC_FACTORY_RESET.equals(intent.getAction())) {
                        if (getResultCode() != EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                            int detailedCode = intent.getIntExtra(
                                    EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0);
                            Log.e(TAG, "Error wiping euicc data, Detailed code = "
                                    + detailedCode);
                        } else {
                            Log.d(TAG, "Successfully wiped euicc data.");
                            wipingSucceeded.set(true /* newValue */);
                        }
                        euiccFactoryResetLatch.countDown();
                    }
                }
            };

            Intent intent = new Intent(ACTION_EUICC_FACTORY_RESET);
            intent.setPackage(packageName);
            PendingIntent callbackIntent = PendingIntent.getBroadcastAsUser(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, UserHandle.SYSTEM);
            IntentFilter filterConsent = new IntentFilter();
            filterConsent.addAction(ACTION_EUICC_FACTORY_RESET);
            HandlerThread euiccHandlerThread = new HandlerThread("euiccWipeFinishReceiverThread");
            euiccHandlerThread.start();
            Handler euiccHandler = new Handler(euiccHandlerThread.getLooper());
            context.getApplicationContext()
                    .registerReceiver(euiccWipeFinishReceiver, filterConsent, null, euiccHandler);
            euiccManager.eraseSubscriptions(callbackIntent);
            try {
                long waitingTimeMillis = Settings.Global.getLong(
                        context.getContentResolver(),
                        Settings.Global.EUICC_FACTORY_RESET_TIMEOUT_MILLIS,
                        DEFAULT_EUICC_FACTORY_RESET_TIMEOUT_MILLIS);
                if (waitingTimeMillis < MIN_EUICC_FACTORY_RESET_TIMEOUT_MILLIS) {
                    waitingTimeMillis = MIN_EUICC_FACTORY_RESET_TIMEOUT_MILLIS;
                } else if (waitingTimeMillis > MAX_EUICC_FACTORY_RESET_TIMEOUT_MILLIS) {
                    waitingTimeMillis = MAX_EUICC_FACTORY_RESET_TIMEOUT_MILLIS;
                }
                if (!euiccFactoryResetLatch.await(waitingTimeMillis, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout wiping eUICC data.");
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Wiping eUICC data interrupted", e);
                return false;
            } finally {
                context.getApplicationContext().unregisterReceiver(euiccWipeFinishReceiver);
            }
            return wipingSucceeded.get();
        }
        return false;
    }

    private static void removeEuiccInvisibleSubs(
            Context context, EuiccManager euiccManager) {
        ContentResolver cr = context.getContentResolver();
        if (Settings.Global.getInt(cr, Settings.Global.EUICC_PROVISIONED, 0) == 0) {
            // If the eUICC isn't provisioned, there's no need to remove euicc invisible profiles,
            // as there's nothing to be removed.
            Log.i(TAG, "Skip removing eUICC invisible profiles as it is not provisioned.");
            return;
        } else if (euiccManager == null || !euiccManager.isEnabled()) {
            Log.i(TAG, "Skip removing eUICC invisible profiles as eUICC manager is not available.");
            return;
        }
        SubscriptionManager subscriptionManager =
                context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> availableSubs =
                subscriptionManager.getAvailableSubscriptionInfoList();
        if (availableSubs == null || availableSubs.isEmpty()) {
            Log.i(TAG, "Skip removing eUICC invisible profiles as no available profiles found.");
            return;
        }
        List<SubscriptionInfo> invisibleSubs = new ArrayList<>();
        for (SubscriptionInfo sub : availableSubs) {
            if (sub.isEmbedded() && sub.getGroupUuid() != null && sub.isOpportunistic()) {
                invisibleSubs.add(sub);
            }
        }
        removeEuiccInvisibleSubs(context, invisibleSubs, euiccManager);
    }

    private static boolean removeEuiccInvisibleSubs(
            Context context, List<SubscriptionInfo> subscriptionInfos, EuiccManager euiccManager) {
        if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
            Log.i(TAG, "There are no eUICC invisible profiles needed to be removed.");
            return true;
        }
        CountDownLatch removeSubsLatch = new CountDownLatch(subscriptionInfos.size());
        final AtomicInteger removedSubsCount = new AtomicInteger(0);

        BroadcastReceiver removeEuiccSubsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS.equals(intent.getAction())) {
                    if (getResultCode() != EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                        int detailedCode = intent.getIntExtra(
                                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0);
                        Log.e(TAG, "Error removing euicc opportunistic profile, Detailed code = "
                                + detailedCode);
                    } else {
                        Log.e(TAG, "Successfully remove euicc opportunistic profile.");
                        removedSubsCount.incrementAndGet();
                    }
                    removeSubsLatch.countDown();
                }
            }
        };

        Intent intent = new Intent(ACTION_EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS);
        intent.setPackage(PACKAGE_NAME_EUICC_DATA_MANAGEMENT_CALLBACK);
        PendingIntent callbackIntent = PendingIntent.getBroadcastAsUser(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, UserHandle.SYSTEM);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS);
        HandlerThread euiccHandlerThread =
                new HandlerThread("euiccRemovingSubsReceiverThread");
        euiccHandlerThread.start();
        Handler euiccHandler = new Handler(euiccHandlerThread.getLooper());
        context.getApplicationContext()
                .registerReceiver(
                        removeEuiccSubsReceiver, intentFilter, null, euiccHandler);
        for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
            Log.i(
                    TAG,
                    "Remove invisible subscription " + subscriptionInfo.getSubscriptionId()
                            + " from card " + subscriptionInfo.getCardId());
            euiccManager.createForCardId(subscriptionInfo.getCardId())
                    .deleteSubscription(subscriptionInfo.getSubscriptionId(), callbackIntent);
        }
        try {
            long waitingTimeMillis = Settings.Global.getLong(
                    context.getContentResolver(),
                    Settings.Global.EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS,
                    DEFAULT_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS);
            if (waitingTimeMillis < MIN_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS) {
                waitingTimeMillis = MIN_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS;
            } else if (waitingTimeMillis > MAX_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS) {
                waitingTimeMillis = MAX_EUICC_REMOVING_INVISIBLE_PROFILES_TIMEOUT_MILLIS;
            }
            if (!removeSubsLatch.await(waitingTimeMillis, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timeout removing invisible euicc profiles.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Removing invisible euicc profiles interrupted", e);
            return false;
        } finally {
            context.getApplicationContext().unregisterReceiver(removeEuiccSubsReceiver);
            if (euiccHandlerThread != null) {
                euiccHandlerThread.quit();
            }
        }
        return removedSubsCount.get() == subscriptionInfos.size();
    }

    /** {@hide} */
    public static void rebootPromptAndWipeUserData(Context context, String reason)
            throws IOException {
        boolean checkpointing = false;
        boolean needReboot = false;
        IVold vold = null;
        try {
            vold = IVold.Stub.asInterface(ServiceManager.checkService("vold"));
            if (vold != null) {
                checkpointing = vold.needsCheckpoint();
            } else  {
                Log.w(TAG, "Failed to get vold");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check for checkpointing");
        }

        // If we are running in checkpointing mode, we should not prompt a wipe.
        // Checkpointing may save us. If it doesn't, we will wind up here again.
        if (checkpointing) {
            try {
                vold.abortChanges("rescueparty", false);
                Log.i(TAG, "Rescue Party requested wipe. Aborting update");
            } catch (Exception e) {
                Log.i(TAG, "Rescue Party requested wipe. Rebooting instead.");
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                pm.reboot("rescueparty");
            }
            return;
        }

        String reasonArg = null;
        if (!TextUtils.isEmpty(reason)) {
            reasonArg = "--reason=" + sanitizeArg(reason);
        }

        final String localeArg = "--locale=" + Locale.getDefault().toString();
        bootCommand(context, null, "--prompt_and_wipe_data", reasonArg, localeArg);
    }

    /**
     * Reboot into the recovery system to wipe the /cache partition.
     * @throws IOException if something goes wrong.
     */
    public static void rebootWipeCache(Context context) throws IOException {
        rebootWipeCache(context, context.getPackageName());
    }

    /** {@hide} */
    public static void rebootWipeCache(Context context, String reason) throws IOException {
        String reasonArg = null;
        if (!TextUtils.isEmpty(reason)) {
            reasonArg = "--reason=" + sanitizeArg(reason);
        }

        final String localeArg = "--locale=" + Locale.getDefault().toLanguageTag() ;
        bootCommand(context, "--wipe_cache", reasonArg, localeArg);
    }

    /**
     * Reboot into recovery and wipe the A/B device.
     *
     * @param Context      the Context to use.
     * @param packageFile  the wipe package to be applied.
     * @param reason       the reason to wipe.
     *
     * @throws IOException if something goes wrong.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(allOf = {
            android.Manifest.permission.RECOVERY,
            android.Manifest.permission.REBOOT
    })
    public static void rebootWipeAb(Context context, File packageFile, String reason)
            throws IOException {
        String reasonArg = null;
        if (!TextUtils.isEmpty(reason)) {
            reasonArg = "--reason=" + sanitizeArg(reason);
        }

        final String filename = packageFile.getCanonicalPath();
        final String filenameArg = "--wipe_package=" + filename;
        final String localeArg = "--locale=" + Locale.getDefault().toLanguageTag() ;
        bootCommand(context, "--wipe_ab", filenameArg, reasonArg, localeArg);
    }

    /**
     * Reboot into the recovery system with the supplied argument.
     * @param args to pass to the recovery utility.
     * @throws IOException if something goes wrong.
     */
    private static void bootCommand(Context context, String... args) throws IOException {
        LOG_FILE.delete();

        StringBuilder command = new StringBuilder();
        for (String arg : args) {
            if (!TextUtils.isEmpty(arg)) {
                command.append(arg);
                command.append("\n");
            }
        }

        // Write the command into BCB (bootloader control block) and boot from
        // there. Will not return unless failed.
        RecoverySystem rs = (RecoverySystem) context.getSystemService(Context.RECOVERY_SERVICE);
        rs.rebootRecoveryWithCommand(command.toString());

        throw new IOException("Reboot failed (no permissions?)");
    }

    /**
     * Called after booting to process and remove recovery-related files.
     * @return the log file from recovery, or null if none was found.
     *
     * @hide
     */
    public static String handleAftermath(Context context) {
        // Record the tail of the LOG_FILE
        String log = null;
        try {
            log = FileUtils.readTextFile(LOG_FILE, -LOG_FILE_MAX_LENGTH, "...\n");
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No recovery log file");
        } catch (IOException e) {
            Log.e(TAG, "Error reading recovery log", e);
        }


        // Only remove the OTA package if it's partially processed (uncrypt'd).
        boolean reservePackage = BLOCK_MAP_FILE.exists();
        if (!reservePackage && UNCRYPT_PACKAGE_FILE.exists()) {
            String filename = null;
            try {
                filename = FileUtils.readTextFile(UNCRYPT_PACKAGE_FILE, 0, null);
            } catch (IOException e) {
                Log.e(TAG, "Error reading uncrypt file", e);
            }

            // Remove the OTA package on /data that has been (possibly
            // partially) processed. (Bug: 24973532)
            if (filename != null && filename.startsWith("/data")) {
                if (UNCRYPT_PACKAGE_FILE.delete()) {
                    Log.i(TAG, "Deleted: " + filename);
                } else {
                    Log.e(TAG, "Can't delete: " + filename);
                }
            }
        }

        // We keep the update logs (beginning with LAST_PREFIX), and optionally
        // the block map file (BLOCK_MAP_FILE) for a package. BLOCK_MAP_FILE
        // will be created at the end of a successful uncrypt. If seeing this
        // file, we keep the block map file and the file that contains the
        // package name (UNCRYPT_PACKAGE_FILE). This is to reduce the work for
        // GmsCore to avoid re-downloading everything again.
        String[] names = RECOVERY_DIR.list();
        for (int i = 0; names != null && i < names.length; i++) {
            // Do not remove the last_install file since the recovery-persist takes care of it.
            if (names[i].startsWith(LAST_PREFIX) || names[i].equals(LAST_INSTALL_PATH)) continue;
            if (reservePackage && names[i].equals(BLOCK_MAP_FILE.getName())) continue;
            if (reservePackage && names[i].equals(UNCRYPT_PACKAGE_FILE.getName())) continue;

            recursiveDelete(new File(RECOVERY_DIR, names[i]));
        }

        return log;
    }

    /**
     * Internally, delete a given file or directory recursively.
     */
    private static void recursiveDelete(File name) {
        if (name.isDirectory()) {
            String[] files = name.list();
            for (int i = 0; files != null && i < files.length; i++) {
                File f = new File(name, files[i]);
                recursiveDelete(f);
            }
        }

        if (!name.delete()) {
            Log.e(TAG, "Can't delete: " + name);
        } else {
            Log.i(TAG, "Deleted: " + name);
        }
    }

    /**
     * Talks to RecoverySystemService via Binder to trigger uncrypt.
     */
    private boolean uncrypt(String packageFile, IRecoverySystemProgressListener listener) {
        try {
            return mService.uncrypt(packageFile, listener);
        } catch (RemoteException unused) {
        }
        return false;
    }

    /**
     * Talks to RecoverySystemService via Binder to set up the BCB.
     */
    private boolean setupBcb(String command) {
        try {
            return mService.setupBcb(command);
        } catch (RemoteException unused) {
        }
        return false;
    }

    /**
     * Talks to RecoverySystemService via Binder to clear up the BCB.
     */
    private boolean clearBcb() {
        try {
            return mService.clearBcb();
        } catch (RemoteException unused) {
        }
        return false;
    }

    /**
     * Talks to RecoverySystemService via Binder to set up the BCB command and
     * reboot into recovery accordingly.
     */
    private void rebootRecoveryWithCommand(String command) {
        try {
            mService.rebootRecoveryWithCommand(command);
        } catch (RemoteException ignored) {
        }
    }

    /**
     * Begins the process of asking the user for the Lock Screen Knowledge Factor.
     *
     * @param updateToken token that will be used in calls to {@link #rebootAndApply} to ensure
     *                    that the preparation was for the correct update
     * @return true if the request was correct
     * @throws IOException if the recovery system service could not be contacted
     */
    private boolean requestLskf(String updateToken, IntentSender sender) throws IOException {
        try {
            return mService.requestLskf(updateToken, sender);
        } catch (RemoteException e) {
            throw new IOException("could request update");
        }
    }

    /**
     * Calls the recovery system service and clears the setup for the OTA.
     *
     * @return true if the setup for OTA was cleared
     * @throws IOException if the recovery system service could not be contacted
     */
    private boolean clearLskf() throws IOException {
        try {
            return mService.clearLskf();
        } catch (RemoteException e) {
            throw new IOException("could not clear LSKF");
        }
    }

    /**
     * Calls the recovery system service to reboot and apply update.
     *
     * @param updateToken the update token for which the update was prepared
     */
    private boolean rebootWithLskf(String updateToken, String reason) throws IOException {
        try {
            return mService.rebootWithLskf(updateToken, reason);
        } catch (RemoteException e) {
            throw new IOException("could not reboot for update");
        }
    }

    /**
     * Internally, recovery treats each line of the command file as a separate
     * argv, so we only need to protect against newlines and nulls.
     */
    private static String sanitizeArg(String arg) {
        arg = arg.replace('\0', '?');
        arg = arg.replace('\n', '?');
        return arg;
    }


    /**
     * @removed Was previously made visible by accident.
     */
    public RecoverySystem() {
        mService = null;
    }

    /**
     * @hide
     */
    public RecoverySystem(IRecoverySystem service) {
        mService = service;
    }
}
