package com.pystudio.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// S-6.3: GitService Kotlin - API de haut niveau

data class GitStatus(
    val currentBranch: String,
    val ahead: Int,
    val behind: Int,
    val modifiedFiles: List<String>,
    val untrackedFiles: List<String>,
    val stagedFiles: List<String>,
    val conflictedFiles: List<String>
)

data class TransferProgress(
    val operation: String,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val objectsProcessed: Int,
    val totalObjects: Int?
)

data class CommitLog(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: Long
)

class GitRepositoryService {
    init {
        System.loadLibrary("gitengine")
    }

    private val _cloneProgress = MutableStateFlow<TransferProgress?>(null)
    fun cloneProgress(): Flow<TransferProgress?> = _cloneProgress

    external fun nativeClone(url: String, destPath: String, username: String, token: String): Boolean
    external fun nativeOpen(repoPath: String): Boolean
    external fun nativeGetStatus(repoPath: String): GitStatus
    external fun nativeStageFile(repoPath: String, filePath: String): Boolean
    external fun nativeUnstageFile(repoPath: String, filePath: String): Boolean
    external fun nativeCommit(repoPath: String, message: String, authorName: String, authorEmail: String): Boolean
    external fun nativeCreateBranch(repoPath: String, name: String): Boolean
    external fun nativeCheckoutBranch(repoPath: String, name: String): Boolean
    external fun nativeDeleteBranch(repoPath: String, name: String): Boolean
    external fun nativeListBranches(repoPath: String): List<String>
    external fun nativeDiff(repoPath: String, filePath: String): String
    external fun nativeLog(repoPath: String, maxCount: Int): List<CommitLog>

    fun clone(url: String, destPath: String, username: String = "", token: String = ""): Boolean {
        return nativeClone(url, destPath, username, token)
    }

    fun open(repoPath: String): Boolean {
        return nativeOpen(repoPath)
    }

    fun status(repoPath: String): GitStatus {
        return nativeGetStatus(repoPath)
    }

    fun stageFile(repoPath: String, filePath: String): Boolean {
        return nativeStageFile(repoPath, filePath)
    }

    fun unstageFile(repoPath: String, filePath: String): Boolean {
        return nativeUnstageFile(repoPath, filePath)
    }

    fun commit(repoPath: String, message: String, authorName: String, authorEmail: String): Boolean {
        return nativeCommit(repoPath, message, authorName, authorEmail)
    }

    fun createBranch(repoPath: String, name: String): Boolean {
        return nativeCreateBranch(repoPath, name)
    }

    fun checkoutBranch(repoPath: String, name: String): Boolean {
        return nativeCheckoutBranch(repoPath, name)
    }

    fun deleteBranch(repoPath: String, name: String): Boolean {
        return nativeDeleteBranch(repoPath, name)
    }

    fun listBranches(repoPath: String): List<String> {
        return nativeListBranches(repoPath)
    }

    fun diff(repoPath: String, filePath: String): String {
        return nativeDiff(repoPath, filePath)
    }

    fun log(repoPath: String, maxCount: Int): List<CommitLog> {
        return nativeLog(repoPath, maxCount)
    }
}

class GitSyncService {
    init {
        System.loadLibrary("gitengine")
    }

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    fun transferProgress(): Flow<TransferProgress?> = _transferProgress

    external fun nativePush(repoPath: String, remoteName: String, username: String, token: String): Boolean
    external fun nativePull(repoPath: String, remoteName: String, username: String, token: String): Boolean

    fun push(repoPath: String, remoteName: String = "origin", username: String = "", token: String = ""): Boolean {
        return nativePush(repoPath, remoteName, username, token)
    }

    fun pull(repoPath: String, remoteName: String = "origin", username: String = "", token: String = ""): Boolean {
        return nativePull(repoPath, remoteName, username, token)
    }
}

class GitMergeService {
    init {
        System.loadLibrary("gitengine")
    }

    external fun nativeMerge(repoPath: String, sourceBranch: String): Boolean
    external fun nativeRebase(repoPath: String, targetBranch: String): Boolean

    fun merge(repoPath: String, sourceBranch: String): Boolean {
        return nativeMerge(repoPath, sourceBranch)
    }

    fun rebase(repoPath: String, targetBranch: String): Boolean {
        return nativeRebase(repoPath, targetBranch)
    }
}

// S-6.4: Gestion des credentials (Android Keystore, SSH keys chiffrées)
class GitAuthService(private val context: Context) {
    private val PREFS_NAME = "git_auth_prefs"
    private val KEY_ALIAS = "git_credentials_key"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun storeCredential(remoteUrl: String, token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        
        val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.DEFAULT)
        
        val storedValue = "$ivBase64:$ciphertextBase64"
        val alias = "keystore_alias_${remoteUrl.hashCode()}"
        
        prefs.edit().putString(alias, storedValue).apply()
        prefs.edit().putString("${alias}_url", remoteUrl).apply()
        
        return alias
    }

    fun getCredential(alias: String): String {
        val storedValue = prefs.getString(alias, null) ?: return ""
        val parts = storedValue.split(":")
        if (parts.size != 2) return ""
        
        try {
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val ciphertext = Base64.decode(parts[1], Base64.DEFAULT)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun deleteCredential(alias: String) {
        prefs.edit().remove(alias).remove("${alias}_url").apply()
    }

    fun listStoredRemotes(): List<String> {
        val remotes = mutableListOf<String>()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.endsWith("_url") && value is String) {
                remotes.add(value)
            }
        }
        return remotes
    }

    fun storeSshKey(alias: String, pemFile: File): Boolean {
        if (!pemFile.exists()) return false
        try {
            val pemContent = pemFile.readText(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(pemContent.toByteArray(Charsets.UTF_8))
            
            val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)
            val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.DEFAULT)
            
            val storedValue = "$ivBase64:$ciphertextBase64"
            prefs.edit().putString("ssh_key_$alias", storedValue).apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getSshKey(alias: String): String {
        val storedValue = prefs.getString("ssh_key_$alias", null) ?: return ""
        val parts = storedValue.split(":")
        if (parts.size != 2) return ""
        
        try {
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val ciphertext = Base64.decode(parts[1], Base64.DEFAULT)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
