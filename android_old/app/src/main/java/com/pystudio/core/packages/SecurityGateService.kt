package com.pystudio.core.packages

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import android.util.Base64

/**
 * SecurityGateService performs cryptographic verification of package artifacts
 * before they are installed. It enforces:
 *
 * 1. **SHA-256 integrity check** — the file hash must match the expected hash
 *    stored in [ArtifactRef.sha256].
 * 2. **Optional RSA signature check** — if a `.asc` detached signature is present,
 *    it is verified against a trusted public key bundled in app assets.
 *
 * Verification matrix:
 * | Scenario                                          | Result        |
 * |---------------------------------------------------|---------------|
 * | SHA-256 mismatch (any source)                     | FAILED        |
 * | Remote artifact, no signature                     | FAILED        |
 * | Remote artifact, invalid signature                | FAILED        |
 * | Remote artifact, valid SHA-256 + valid signature  | OK            |
 * | Local artifact, no signature, allowUnsigned=true  | SKIPPED_LOCAL |
 * | Local artifact, no signature, allowUnsigned=false | FAILED        |
 * | Local artifact, invalid signature                 | FAILED        |
 * | Local artifact, valid SHA-256 + valid signature   | OK            |
 */
class SecurityGateService(private val appContext: Context) {

    companion object {
        private const val TAG = "SecurityGate"
        private const val TRUSTED_KEY_ASSET = "security/trusted_signing_key.pem"
        private const val SHA256_BUFFER_SIZE = 8192
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }

    /** Lazily loaded trusted public key from app assets. */
    private val trustedPublicKey: PublicKey? by lazy {
        loadTrustedPublicKey()
    }

    /**
     * Verify an artifact's integrity and authenticity.
     *
     * @param artifact      the artifact reference containing file path and expected SHA-256
     * @param allowUnsignedLocal if true, local builds without a signature get SKIPPED_LOCAL
     *                           instead of FAILED (SHA-256 is still checked)
     * @return the verification verdict
     */
    suspend fun verify(
        artifact: ArtifactRef,
        allowUnsignedLocal: Boolean = true
    ): VerificationResult = withContext(Dispatchers.IO) {
        val label = "${artifact.name}==${artifact.version}"
        Log.i(TAG, "Verifying artifact: $label at ${artifact.fileAbsolutePath}")

        val artifactFile = File(artifact.fileAbsolutePath)
        if (!artifactFile.exists() || !artifactFile.isFile) {
            Log.e(TAG, "[$label] Artifact file does not exist: ${artifact.fileAbsolutePath}")
            return@withContext VerificationResult.FAILED
        }

        // ── Step 1: SHA-256 integrity check ──────────────────────────────
        val computedHash = computeSha256(artifactFile)
        if (computedHash == null) {
            Log.e(TAG, "[$label] Failed to compute SHA-256 hash")
            return@withContext VerificationResult.FAILED
        }

        val expectedHash = artifact.sha256.lowercase().trim()
        if (expectedHash.isNotEmpty() && computedHash != expectedHash) {
            Log.e(
                TAG,
                "[$label] SHA-256 mismatch: expected=$expectedHash, computed=$computedHash"
            )
            return@withContext VerificationResult.FAILED
        }

        if (expectedHash.isEmpty()) {
            Log.w(TAG, "[$label] No expected SHA-256 provided — integrity cannot be verified")
            return@withContext VerificationResult.FAILED
        }

        Log.d(TAG, "[$label] SHA-256 OK: $computedHash")

        // ── Step 2: Signature verification ───────────────────────────────
        val signatureFile = artifact.signaturePath?.let { File(it) }
        val hasSignature = signatureFile != null && signatureFile.exists() && signatureFile.isFile

        if (hasSignature) {
            val signatureValid = verifySignature(artifactFile, signatureFile!!)
            if (signatureValid) {
                Log.i(TAG, "[$label] Signature OK — verdict: OK")
                return@withContext VerificationResult.OK
            } else {
                Log.e(TAG, "[$label] Signature INVALID — verdict: FAILED")
                return@withContext VerificationResult.FAILED
            }
        }

        // No signature present
        if (artifact.isLocal) {
            if (allowUnsignedLocal) {
                Log.i(
                    TAG,
                    "[$label] Local artifact, no signature, unsigned allowed — verdict: SKIPPED_LOCAL"
                )
                return@withContext VerificationResult.SKIPPED_LOCAL
            } else {
                Log.e(
                    TAG,
                    "[$label] Local artifact, no signature, unsigned NOT allowed — verdict: FAILED"
                )
                return@withContext VerificationResult.FAILED
            }
        }

        // Remote artifact without signature is never acceptable
        Log.e(TAG, "[$label] Remote artifact without signature — verdict: FAILED")
        return@withContext VerificationResult.FAILED
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    /**
     * Compute SHA-256 hash of a file, returned as a lowercase hex string.
     */
    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(SHA256_BUFFER_SIZE)

            FileInputStream(file).use { fis ->
                var bytesRead = fis.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = fis.read(buffer)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing SHA-256 for ${file.name}", e)
            null
        }
    }

    /**
     * Verify a detached RSA signature (.asc) against the artifact file using
     * the trusted public key from app assets.
     *
     * The .asc file is expected to contain a raw or Base64-encoded PKCS#1 v1.5
     * (SHA256withRSA) signature.
     */
    private fun verifySignature(artifactFile: File, signatureFile: File): Boolean {
        val publicKey = trustedPublicKey
        if (publicKey == null) {
            Log.e(TAG, "No trusted public key available — cannot verify signature")
            return false
        }

        return try {
            val signatureBytes = loadSignatureBytes(signatureFile)
            if (signatureBytes == null || signatureBytes.isEmpty()) {
                Log.e(TAG, "Could not read signature from ${signatureFile.name}")
                return false
            }

            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)

            val buffer = ByteArray(SHA256_BUFFER_SIZE)
            FileInputStream(artifactFile).use { fis ->
                var bytesRead = fis.read(buffer)
                while (bytesRead != -1) {
                    sig.update(buffer, 0, bytesRead)
                    bytesRead = fis.read(buffer)
                }
            }

            sig.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error for ${artifactFile.name}", e)
            false
        }
    }

    /**
     * Load signature bytes from a .asc file.
     * Supports both raw binary signatures and Base64-encoded (PEM-style) signatures.
     */
    private fun loadSignatureBytes(signatureFile: File): ByteArray? {
        return try {
            val rawBytes = signatureFile.readBytes()

            // Check if it looks like Base64/PEM encoded
            val text = rawBytes.toString(Charsets.US_ASCII).trim()
            if (text.startsWith("-----BEGIN")) {
                // Strip PEM headers/footers and decode Base64
                val base64Content = text
                    .lines()
                    .filter { line ->
                        !line.startsWith("-----BEGIN") && !line.startsWith("-----END")
                    }
                    .joinToString("")
                    .trim()
                Base64.decode(base64Content, Base64.DEFAULT)
            } else {
                // Assume raw binary
                rawBytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading signature file ${signatureFile.name}", e)
            null
        }
    }

    /**
     * Load the trusted RSA public key from the app's assets directory.
     * The key is expected in PEM format (X.509 SubjectPublicKeyInfo).
     */
    private fun loadTrustedPublicKey(): PublicKey? {
        return try {
            val pemContent = appContext.assets.open(TRUSTED_KEY_ASSET)
                .bufferedReader()
                .use { it.readText() }

            val base64Key = pemContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s+".toRegex(), "")

            val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trusted public key from assets", e)
            null
        }
    }
}
