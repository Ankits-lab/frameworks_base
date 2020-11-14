/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.backup.encryption.chunking.cdc;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure HKDF utils. Allows client to deterministically derive additional key material from a base
 * secret. If the derived key material is compromised, this does not in of itself compromise the
 * root secret.
 *
 * <p>TODO(b/116575321): After all code is ported, rename this class to HkdfUtils.
 */
public final class Hkdf {
    private static final byte[] CONSTANT_01 = {0x01};
    private static final String HmacSHA256 = "HmacSHA256";
    private static final String AES = "AES";

    /**
     * Implements HKDF (RFC 5869) with the SHA-256 hash and a 256-bit output key length.
     *
     * <p>IMPORTANT: The use or edit of this method requires a security review.
     *
     * @param masterKey Master key from which to derive sub-keys.
     * @param salt A randomly generated 256-bit byte string.
     * @param data Arbitrary information that is bound to the derived key (i.e., used in its
     *     creation).
     * @return Raw derived key bytes = HKDF-SHA256(masterKey, salt, data).
     * @throws InvalidKeyException If the salt can not be used as a valid key.
     */
    static byte[] hkdf(byte[] masterKey, byte[] salt, byte[] data) throws InvalidKeyException {
        Objects.requireNonNull(masterKey, "HKDF requires master key to be set.");
        Objects.requireNonNull(salt, "HKDF requires a salt.");
        Objects.requireNonNull(data, "No data provided to HKDF.");
        return hkdfSha256Expand(hkdfSha256Extract(masterKey, salt), data);
    }

    private Hkdf() {}

    /**
     * The HKDF (RFC 5869) extraction function, using the SHA-256 hash function. This function is
     * used to pre-process the {@code inputKeyMaterial} and mix it with the {@code salt}, producing
     * output suitable for use with HKDF expansion function (which produces the actual derived key).
     *
     * <p>IMPORTANT: The use or edit of this method requires a security review.
     *
     * @see #hkdfSha256Expand(byte[], byte[])
     * @return HMAC-SHA256(salt, inputKeyMaterial) (salt is the "key" for the HMAC)
     * @throws InvalidKeyException If the salt can not be used as a valid key.
     */
    private static byte[] hkdfSha256Extract(byte[] inputKeyMaterial, byte[] salt)
            throws InvalidKeyException {
        // Note that the SecretKey encoding format is defined to be RAW, so the encoded form should
        // be consistent across implementations.
        Mac sha256;
        try {
            sha256 = Mac.getInstance(HmacSHA256);
        } catch (NoSuchAlgorithmException e) {
            // This can not happen - HmacSHA256 is supported by the platform.
            throw new AssertionError(e);
        }
        sha256.init(new SecretKeySpec(salt, AES));

        return sha256.doFinal(inputKeyMaterial);
    }

    /**
     * Special case of HKDF (RFC 5869) expansion function, using the SHA-256 hash function and
     * allowing for a maximum output length of 256 bits.
     *
     * <p>IMPORTANT: The use or edit of this method requires a security review.
     *
     * @param pseudoRandomKey Generated by {@link #hkdfSha256Extract(byte[], byte[])}.
     * @param info Arbitrary information the derived key should be bound to.
     * @return Raw derived key bytes = HMAC-SHA256(pseudoRandomKey, info | 0x01).
     * @throws InvalidKeyException If the salt can not be used as a valid key.
     */
    private static byte[] hkdfSha256Expand(byte[] pseudoRandomKey, byte[] info)
            throws InvalidKeyException {
        // Note that RFC 5869 computes number of blocks N = ceil(hash length / output length), but
        // here we only deal with a 256 bit hash up to a 256 bit output, yielding N=1.
        Mac sha256;
        try {
            sha256 = Mac.getInstance(HmacSHA256);
        } catch (NoSuchAlgorithmException e) {
            // This can not happen - HmacSHA256 is supported by the platform.
            throw new AssertionError(e);
        }
        sha256.init(new SecretKeySpec(pseudoRandomKey, AES));

        sha256.update(info);
        sha256.update(CONSTANT_01);
        return sha256.doFinal();
    }
}
