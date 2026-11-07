package dev.breadcrumb.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalSettings(context: Context) {
    private val preferences = context.getSharedPreferences("breadcrumb", Context.MODE_PRIVATE)

    var welcomeCompleted: Boolean
        get() = preferences.getBoolean("welcomeCompleted", false)
        set(value) { preferences.edit().putBoolean("welcomeCompleted", value).apply() }

    var notebookUri: Uri?
        get() = preferences.getString("notebook", null)?.let(Uri::parse)
        set(value) { preferences.edit().putString("notebook", value?.toString()).apply() }

    var notebookName: String
        get() = preferences.getString("notebookName", "Notebook").orEmpty()
        set(value) { preferences.edit().putString("notebookName", value).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("breadcrumb.search", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("breadcrumb.search", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun searchKey(): String = runCatching {
        val encoded = preferences.getString("searchKey", null) ?: return ""
        val parts = encoded.split('.')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")

    fun setSearchKey(value: String) {
        if (value.isBlank()) {
            preferences.edit().remove("searchKey").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        preferences.edit().putString("searchKey", encoded).apply()
    }
}
