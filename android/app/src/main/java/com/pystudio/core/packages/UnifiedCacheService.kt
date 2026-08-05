package com.pystudio.core.packages

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Unified caching service managing L3 (wheel files), L5 (dependency resolution lockfiles),
 * and L6 (environment snapshots) caches. Each level has its own dedicated directory under
 * the application's internal storage.
 *
 * L5 cache entries are JSON files keyed by TOML hash, containing a serialized
 * [PystudioLock] wrapped with a creation timestamp for TTL-based expiration.
 */
class UnifiedCacheService(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedCacheService"

        /** Default time-to-live for L5 cache entries: 24 hours in milliseconds. */
        private val DEFAULT_L5_TTL_MS: Long = TimeUnit.HOURS.toMillis(24)
    }

    val l3CacheDir = File(context.filesDir, "cache/l3_wheels").apply { mkdirs() }
    val l5CacheDir = File(context.filesDir, "cache/l5_resolutions").apply { mkdirs() }
    val l6CacheDir = File(context.filesDir, "cache/l6_environments").apply { mkdirs() }

    /** Configurable TTL for L5 resolution cache entries, in milliseconds. */
    var l5TtlMs: Long = DEFAULT_L5_TTL_MS

    // ─────────────────────────────────────────────────────────────────────────
    // L5 Resolution Cache
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks the L5 cache for a previously resolved lockfile matching [tomlHash].
     *
     * @param tomlHash SHA/hash of the pyproject.toml content, used as the cache key.
     * @return The deserialized [PystudioLock] if a valid, non-expired entry exists; null otherwise.
     */
    fun checkL5Resolution(tomlHash: String): PystudioLock? {
        val file = File(l5CacheDir, "$tomlHash.json")
        if (!file.exists()) {
            Log.d(TAG, "L5 cache miss: no file for hash=$tomlHash")
            return null
        }

        return try {
            val content = file.readText(Charsets.UTF_8)
            val wrapper = JSONObject(content)

            val createdAt = wrapper.getLong("createdAt")
            val age = System.currentTimeMillis() - createdAt

            if (age > l5TtlMs) {
                Log.i(TAG, "L5 cache expired for hash=$tomlHash (age=${TimeUnit.MILLISECONDS.toMinutes(age)}min)")
                file.delete()
                return null
            }

            val lockJson = wrapper.getJSONObject("lockfile")
            deserializePystudioLock(lockJson).also {
                Log.d(TAG, "L5 cache hit for hash=$tomlHash (${it.packages.size} packages)")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "L5 cache deserialization failed for hash=$tomlHash, removing corrupt entry", e)
            file.delete()
            null
        } catch (e: IOException) {
            Log.e(TAG, "L5 cache read failed for hash=$tomlHash", e)
            null
        }
    }

    /**
     * Stores a resolved lockfile in the L5 cache.
     *
     * @param tomlHash SHA/hash of the pyproject.toml content, used as the cache key.
     * @param lockfile The fully resolved [PystudioLock] to persist.
     */
    fun storeL5Resolution(tomlHash: String, lockfile: PystudioLock) {
        val file = File(l5CacheDir, "$tomlHash.json")
        try {
            val wrapper = JSONObject().apply {
                put("createdAt", System.currentTimeMillis())
                put("lockfile", serializePystudioLock(lockfile))
            }
            file.writeText(wrapper.toString(2), Charsets.UTF_8)
            Log.i(TAG, "L5 cache stored for hash=$tomlHash (${lockfile.packages.size} packages)")
        } catch (e: JSONException) {
            Log.e(TAG, "L5 cache serialization failed for hash=$tomlHash", e)
        } catch (e: IOException) {
            Log.e(TAG, "L5 cache write failed for hash=$tomlHash", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // L3 Wheel Cache (already correct — preserved as-is)
    // ─────────────────────────────────────────────────────────────────────────

    fun checkL3Wheel(packageName: String, version: String): File? {
        val files = l3CacheDir.listFiles { _, name ->
            name.startsWith("$packageName-$version", ignoreCase = true) && name.endsWith(".whl")
        }
        return files?.firstOrNull()
    }
    
    fun storeL3Wheel(wheelFile: File) {
        val dest = File(l3CacheDir, wheelFile.name)
        if (!dest.exists()) {
            wheelFile.copyTo(dest)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clears the entire L3 (wheels) cache directory.
     * @return The number of files deleted.
     */
    fun clearL3Cache(): Int {
        return clearDirectory(l3CacheDir)
    }

    /**
     * Clears the entire L5 (resolution lockfiles) cache directory.
     * @return The number of files deleted.
     */
    fun clearL5Cache(): Int {
        return clearDirectory(l5CacheDir)
    }

    /**
     * Clears all cache directories (L3, L5, L6).
     * @return The total number of files deleted.
     */
    fun clearAll(): Int {
        val count = clearDirectory(l3CacheDir) + clearDirectory(l5CacheDir) + clearDirectory(l6CacheDir)
        Log.i(TAG, "Cleared all caches: $count files removed")
        return count
    }

    /**
     * Calculates the total disk usage across all cache directories (L3, L5, L6).
     * @return Total size in bytes.
     */
    fun getCacheSize(): Long {
        return directorySize(l3CacheDir) + directorySize(l5CacheDir) + directorySize(l6CacheDir)
    }

    /**
     * Evicts cache entries older than [maxAgeDays] days across all cache directories.
     * For L5 entries, uses the embedded `createdAt` timestamp if available, falling back
     * to the file's last-modified timestamp.
     *
     * @param maxAgeDays Maximum age in days; entries older than this are deleted.
     * @return The number of files evicted.
     */
    fun evictOldEntries(maxAgeDays: Int): Int {
        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        var evicted = 0

        evicted += evictFromDirectory(l3CacheDir, cutoffMs, useEmbeddedTimestamp = false)
        evicted += evictFromDirectory(l5CacheDir, cutoffMs, useEmbeddedTimestamp = true)
        evicted += evictFromDirectory(l6CacheDir, cutoffMs, useEmbeddedTimestamp = false)

        Log.i(TAG, "Eviction complete: $evicted entries older than $maxAgeDays days removed")
        return evicted
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: Serialization
    // ─────────────────────────────────────────────────────────────────────────

    private fun serializePystudioLock(lock: PystudioLock): JSONObject {
        return JSONObject().apply {
            put("lockVersion", lock.lockVersion)
            put("generatedAt", lock.generatedAt.time)
            put("pythonTarget", lock.pythonTarget)
            put("resolutionContext", serializeResolutionContext(lock.resolutionContext))
            put("packages", JSONArray().apply {
                for (entry in lock.packages) {
                    put(serializePackageLockEntry(entry))
                }
            })
        }
    }

    private fun serializeResolutionContext(ctx: ResolutionContext): JSONObject {
        return JSONObject().apply {
            put("abi", ctx.abi.tag)
            put("apiLevel", ctx.apiLevel)
            put("pythonVersion", ctx.pythonVersion)
        }
    }

    private fun serializePackageLockEntry(entry: PackageLockEntry): JSONObject {
        return JSONObject().apply {
            put("name", entry.name)
            put("version", entry.version)
            put("source", entry.source)
            put("sha256", entry.sha256)
            put("wheelTag", entry.wheelTag)
            put("signatureVerified", entry.signatureVerified)
            put("dependencies", JSONArray().apply {
                for (dep in entry.dependencies) {
                    put(dep)
                }
            })
        }
    }

    private fun deserializePystudioLock(json: JSONObject): PystudioLock {
        val ctxJson = json.getJSONObject("resolutionContext")
        val abiTag = ctxJson.getString("abi")
        val abi = Abi.entries.firstOrNull { it.tag == abiTag }
            ?: throw JSONException("Unknown ABI tag: $abiTag")

        val resolutionContext = ResolutionContext(
            abi = abi,
            apiLevel = ctxJson.getInt("apiLevel"),
            pythonVersion = ctxJson.getString("pythonVersion")
        )

        val packagesArray = json.getJSONArray("packages")
        val packages = mutableListOf<PackageLockEntry>()
        for (i in 0 until packagesArray.length()) {
            packages.add(deserializePackageLockEntry(packagesArray.getJSONObject(i)))
        }

        return PystudioLock(
            lockVersion = json.optInt("lockVersion", 1),
            generatedAt = Date(json.getLong("generatedAt")),
            pythonTarget = json.getString("pythonTarget"),
            resolutionContext = resolutionContext,
            packages = packages
        )
    }

    private fun deserializePackageLockEntry(json: JSONObject): PackageLockEntry {
        val depsArray = json.getJSONArray("dependencies")
        val deps = mutableListOf<String>()
        for (i in 0 until depsArray.length()) {
            deps.add(depsArray.getString(i))
        }

        return PackageLockEntry(
            name = json.getString("name"),
            version = json.getString("version"),
            source = json.getString("source"),
            sha256 = json.getString("sha256"),
            wheelTag = json.getString("wheelTag"),
            signatureVerified = json.getBoolean("signatureVerified"),
            dependencies = deps
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: File Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun clearDirectory(dir: File): Int {
        val files = dir.listFiles() ?: return 0
        var count = 0
        for (file in files) {
            if (file.isFile && file.delete()) {
                count++
            } else if (file.isDirectory) {
                count += clearDirectory(file)
                file.delete()
            }
        }
        return count
    }

    private fun directorySize(dir: File): Long {
        val files = dir.listFiles() ?: return 0L
        var total = 0L
        for (file in files) {
            total += if (file.isDirectory) {
                directorySize(file)
            } else {
                file.length()
            }
        }
        return total
    }

    /**
     * Evicts files from [dir] whose creation time is before [cutoffMs].
     *
     * @param useEmbeddedTimestamp If true, reads the JSON file's `createdAt` field for the timestamp.
     *   Falls back to [File.lastModified] on parse failure.
     */
    private fun evictFromDirectory(dir: File, cutoffMs: Long, useEmbeddedTimestamp: Boolean): Int {
        val files = dir.listFiles() ?: return 0
        var evicted = 0

        for (file in files) {
            if (!file.isFile) continue

            val timestamp = if (useEmbeddedTimestamp && file.extension == "json") {
                readEmbeddedTimestamp(file) ?: file.lastModified()
            } else {
                file.lastModified()
            }

            if (timestamp < cutoffMs) {
                if (file.delete()) {
                    evicted++
                    Log.d(TAG, "Evicted cache entry: ${file.name}")
                }
            }
        }

        return evicted
    }

    /**
     * Reads the `createdAt` field from a JSON cache wrapper file.
     * @return The timestamp in milliseconds, or null if parsing fails.
     */
    private fun readEmbeddedTimestamp(file: File): Long? {
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            json.getLong("createdAt")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read embedded timestamp from ${file.name}", e)
            null
        }
    }
}
