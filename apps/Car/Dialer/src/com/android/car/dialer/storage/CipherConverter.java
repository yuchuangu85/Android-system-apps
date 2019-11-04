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

package com.android.car.dialer.storage;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.room.TypeConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A converter that does the encryption and decryption using android KeyStore system. See
 * https://developer.android.com/training/articles/keystore
 */
public class CipherConverter {
    private static final String TAG = "CD.CipherConverter";
    private static final String KEY_STORE_ALIAS = "cd-cipher-converter";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    /**
     * Decryption.
     *
     * @param encryptedData the encrypted byte array. First byte is the initialization vector
     *                      length, followed by the
     *                      initialization vector and then the encrypted string.
     * @return the decrypted string wrapper. It might be null if the encrypted array is not valid or
     * exception happens during decryption.
     */
    @WorkerThread
    @TypeConverter
    @Nullable
    public CipherWrapper<String> decrypt(@NonNull byte[] encryptedData) {
        if (encryptedData.length == 0) {
            return null;
        }

        try {
            KeyStore ks = getKeyStore();
            SecretKey decryptionKey = (SecretKey) ks.getKey(KEY_STORE_ALIAS, null);

            Cipher cipher = getCipherInstance();
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(encryptedData);

            int ivLength = byteArrayInputStream.read();

            byte[] iv = new byte[ivLength];
            byteArrayInputStream.read(iv, 0, ivLength);

            byte[] encryptedPhoneNumber = new byte[encryptedData.length - ivLength - 1];
            byteArrayInputStream.read(encryptedPhoneNumber);

            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new GCMParameterSpec(128, iv));
            byte[] decryptionResult = cipher.doFinal(encryptedPhoneNumber);
            String decryptString = new String(decryptionResult, "UTF-8");
            return new CipherWrapper(decryptString);
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException
                | UnrecoverableKeyException | NoSuchPaddingException | BadPaddingException
                | IllegalBlockSizeException | InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }

    /**
     * Encryption.
     *
     * @param stringCipherWrapper The wrapper of string to be encrypted.
     * @return byte array that includes the iv length, iv and encrypted string.
     */
    @WorkerThread
    @NonNull
    @TypeConverter
    public byte[] encrypt(CipherWrapper<String> stringCipherWrapper) {
        try {
            KeyStore ks = getKeyStore();
            SecretKey secretKey;
            if (ks.containsAlias(KEY_STORE_ALIAS)) {
                secretKey = (SecretKey) ks.getKey(KEY_STORE_ALIAS, null);
            } else {
                KeyGenerator kpg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEY_STORE);
                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        KEY_STORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                kpg.init(keyGenParameterSpec);
                secretKey = kpg.generateKey();
            }

            Cipher cipher = getCipherInstance();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(iv.length);
            outputStream.write(iv);
            byte[] encryptionResult = cipher.doFinal(
                    stringCipherWrapper.get().getBytes("UTF-8"));
            outputStream.write(encryptionResult);
            return outputStream.toByteArray();
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException
                | UnrecoverableKeyException | NoSuchProviderException | NoSuchPaddingException
                | BadPaddingException | IllegalBlockSizeException | InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            Log.e(TAG, e.toString());
        }
        return new byte[0];
    }

    private KeyStore getKeyStore()
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return keyStore;
    }

    private Cipher getCipherInstance()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/"
                        + KeyProperties.ENCRYPTION_PADDING_NONE);
    }
}
