package com.pystudio.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AICloudClient(private val context: Context) {

    private val KEY_ALIAS = "ai_api_key_alias"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val PREFS_NAME = "ai_cloud_prefs"
    private val PREF_KEY_CIPHERTEXT = "api_key_ciphertext"
    private val PREF_KEY_IV = "api_key_iv"
    private val GCM_TAG_LENGTH = 128

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Retrieves the decrypted API key from SharedPreferences.
     * Returns null if no key has been configured.
     */
    fun getApiKey(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertextB64 = prefs.getString(PREF_KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(PREF_KEY_IV, null) ?: return null

        return try {
            val ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("AICloudClient", "Failed to decrypt API key", e)
            null
        }
    }

    /**
     * Encrypts and stores the API key in SharedPreferences using AES-GCM
     * with a key from the Android Keystore.
     */
    fun setApiKey(apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_CIPHERTEXT, ciphertextB64)
            .putString(PREF_KEY_IV, ivB64)
            .apply()
    }

    suspend fun generateCompletion(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw Exception("Cloud API Key not configured")
        
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val messages = JSONArray()
        if (systemPrompt != null) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val body = JSONObject()
        body.put("model", "gpt-4-turbo-preview")
        body.put("messages", messages)

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
        }

        if (connection.responseCode == 200) {
            val responseString = connection.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                return@withContext message?.optString("content") ?: ""
            }
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("API Error: ${connection.responseCode} - $errorStream")
        }
        
        return@withContext ""
    }
}
