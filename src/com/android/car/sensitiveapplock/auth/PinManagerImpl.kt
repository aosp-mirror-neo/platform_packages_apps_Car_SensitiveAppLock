/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.sensitiveapplock.auth

import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.car.sensitiveapplock.auth.PinManager.PinState
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.util.Logger
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.subtle.Hex
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Implementation of [PinManager]. */
@Singleton
class PinManagerImpl
@Inject
constructor(
    @BackgroundContext private val backgroundContext: CoroutineContext,
    private val appLockDataRepository: AppLockDataRepository,
    private val sharedPreferences: DataStore<Preferences>,
) : PinManager {
    @VisibleForTesting var aead: Aead? = null

    private val encryptedKeysetStringFlow =
        sharedPreferences.data.map { it[TINK_KEYSET_NAME] ?: "" }

    override suspend fun getAppLockPinState(): PinState =
        withContext(backgroundContext) {
            if (appLockDataRepository.getPin().isEmpty()) {
                return@withContext PinState.UNSET
            }
            val encryptedKeysetExists = encryptedKeysetStringFlow.first().isNotEmpty()
            val masterKeyExists = AndroidKeystore.hasKey(MASTER_KEY_ALIAS)
            // Pin is set, but encryption keys are missing.
            if (!masterKeyExists || !encryptedKeysetExists) {
                return@withContext PinState.RESET_REQUIRED
            }
            return@withContext PinState.SET
        }

    override suspend fun setAppLockPin(pin: String): Boolean =
        withContext(backgroundContext) {
            // Always clear pin encryption data before storing a new pin. This ensures we don't have
            // missing MasterEncryption keys or missing Keysets.
            reset()

            if (!doesPinHaveValidFormat(pin)) {
                logger.i("Cannot store pin, invalid format.")
                return@withContext false
            }
            logger.d("Pin has valid format.")
            if (aead == null && !maybeInitializeKeysetHandleAndAeadKeyset()) {
                logger.i("Cannot store pin; encryption not available.")
                return@withContext false
            }
            logger.v("Encryption available.")
            try {
                aead
                    ?.encrypt(
                        pin.toByteArray(UTF_8),
                        null, // associatedData
                    )
                    ?.let { appLockDataRepository.setPin(Hex.encode(it)) }
                logger.v("Successfully stored the pin.")
                return@withContext true
            } catch (e: GeneralSecurityException) {
                logger.w("Unable to encrypt the pin.", e)
                return@withContext false
            }
        }

    override suspend fun clearAppLockPin() =
        withContext(backgroundContext) { appLockDataRepository.setPin("") }

    override suspend fun verifyAppLockPin(pin: String): Boolean =
        withContext(backgroundContext) {
            if (getAppLockPinState() == PinState.UNSET) {
                logger.w("Cannot verify pin; pin not set.")
                return@withContext false
            }
            if (aead == null && !maybeInitializeKeysetHandleAndAeadKeyset()) {
                logger.i("Cannot verify pin; encryption not available.")
                return@withContext false
            }
            try {
                val decryptedPin =
                    aead
                        ?.decrypt(
                            Hex.decode(appLockDataRepository.getPin()),
                            null, // associatedData
                        )
                        ?.toString(UTF_8)
                return@withContext pin == decryptedPin
            } catch (e: GeneralSecurityException) {
                logger.w("Unable to decrypt the pin.", e)
                return@withContext false
            }
        }

    override suspend fun reset() {
        AndroidKeystore.deleteKey(MASTER_KEY_ALIAS)
        sharedPreferences.edit { preferences -> preferences.remove(TINK_KEYSET_NAME) }
        aead = null
        clearAppLockPin()
    }

    override fun doesPinHaveValidFormat(pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH) {
            return false
        }
        if (pin.length > MAX_PIN_LENGTH) {
            return false
        }
        return pin.toLongOrNull() != null
    }

    private suspend fun maybeInitializeKeysetHandleAndAeadKeyset(): Boolean {
        try {
            TinkConfig.register()
            val encryptedKeysetExists = encryptedKeysetStringFlow.first().isNotEmpty()
            val masterKeyExists = AndroidKeystore.hasKey(MASTER_KEY_ALIAS)

            logger.v(
                "MasterKey exists=$masterKeyExists; EncryptedKeyset exists=$encryptedKeysetExists"
            )

            // The master key is missing. This may happen if the car is restored from a backup.
            // Currently auto does not support restore from backup. This should not happen
            if (!masterKeyExists && encryptedKeysetExists) {
                return false
            }

            // The master key exists, but the encrypted keyset is missing. Here we assume that
            // the master key is only used to encrypt one keyset, so this should not happen.
            if (masterKeyExists && !encryptedKeysetExists) {
                return false
            }

            if (!masterKeyExists && !encryptedKeysetExists) {
                // Create a new master key in Android Keystore
                AndroidKeystore.generateNewAes256GcmKey(MASTER_KEY_ALIAS)
                // Create and store an encrypted keyset
                if (!createAndStoreEncryptedKeyset()) {
                    // delete the master key, this ensures the next time we recreate the keyset
                    AndroidKeystore.deleteKey(MASTER_KEY_ALIAS)
                    return false
                }
            }
            val encryptedKeyset = Hex.decode(encryptedKeysetStringFlow.first())
            val keysetHandle =
                TinkProtoKeysetFormat.parseEncryptedKeyset(
                    encryptedKeyset,
                    AndroidKeystore.getAead(MASTER_KEY_ALIAS),
                    TINK_KEYSET_ASSOCIATED_DATA,
                )
            logger.v("Successfully parsed encrypted keyset.")
            aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return true
        } catch (e: GeneralSecurityException) {
            logger.w("Failed to initialize AndroidKeystore", e)
            return false
        }
    }

    private suspend fun createAndStoreEncryptedKeyset(): Boolean {
        try {
            // Create a new keyset handle
            val keysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            // Encrypt the keyset
            val encryptedKeyset =
                TinkProtoKeysetFormat.serializeEncryptedKeyset(
                    keysetHandle,
                    AndroidKeystore.getAead(MASTER_KEY_ALIAS),
                    TINK_KEYSET_ASSOCIATED_DATA,
                )
            // Store the keyset
            sharedPreferences.edit { preferences ->
                preferences[TINK_KEYSET_NAME] = Hex.encode(encryptedKeyset)
            }
        } catch (e: GeneralSecurityException) {
            logger.w("Unable to create encrypted keyset", e)
            return false
        }
        logger.v("Created and stored encrypted keyset in preferences")
        return true
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 16
        const val MASTER_KEY_ALIAS = "app_lock_master_key"
        val TINK_KEYSET_NAME = stringPreferencesKey("app_lock_keyset")
        val TINK_KEYSET_ASSOCIATED_DATA = ByteArray(0)
        val logger = Logger(PinManager::class.java)
    }
}
