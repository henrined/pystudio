package com.pystudio.core.packages

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DependencyResolverService(private val cache: UnifiedCacheService) {

    data class Requirement(
        val name: String,
        val extras: List<String>,
        val specs: List<Pair<String, String>>,
        val marker: String?
    )

    class Version(versionString: String) : Comparable<Version> {
        val parts: List<Int> = versionString.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }

        override fun compareTo(other: Version): Int {
            val maxLen = maxOf(this.parts.size, other.parts.size)
            for (i in 0 until maxLen) {
                val v1 = this.parts.getOrElse(i) { 0 }
                val v2 = other.parts.getOrElse(i) { 0 }
                if (v1 != v2) return v1.compareTo(v2)
            }
            return 0
        }
    }

    suspend fun resolve(projectToml: PystudioToml, context: ResolutionContext): ResolutionOutcome = withContext(Dispatchers.IO) {
        val tomlHash = projectToml.hashCode().toString()
        val cachedLock = cache.checkL5Resolution(tomlHash)
        if (cachedLock != null) {
            Log.i("DependencyResolver", "L5 Cache hit for resolution")
            return@withContext ResolutionOutcome.Success(cachedLock)
        }
        
        Log.i("DependencyResolver", "Solving dependencies via Greedy Depth-First")
        
        val resolvedPackages = mutableMapOf<String, PackageLockEntry>()
        val processing = mutableSetOf<String>()

        fun fetchPackageData(name: String, version: String? = null): JSONObject {
            val urlStr = if (version != null) {
                "https://pypi.org/pypi/$name/$version/json"
            } else {
                "https://pypi.org/pypi/$name/json"
            }
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                throw Exception("Failed to fetch $name from PyPI: HTTP ${connection.responseCode}")
            }
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(jsonString)
        }

        fun parseRequirement(reqString: String): Requirement {
            val regex = Regex("^([a-zA-Z0-9_.-]+)(?:\\[(.*?)\\])?\\s*(?:\\((.*?)\\)|(>=|<=|==|~=|!=|<|>)\\s*([0-9a-zA-Z.-]+))?(?:\\s*;\\s*(.*))?$")
            val match = regex.find(reqString.trim()) ?: return Requirement(reqString.split(Regex("[\\s\\[=><~;]+"))[0], emptyList(), emptyList(), null)
            
            val name = match.groupValues[1]
            val extras = match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val specGroup3 = match.groupValues[3]
            val specGroup4 = match.groupValues[4]
            val specGroup5 = match.groupValues[5]
            val marker = match.groupValues[6].takeIf { it.isNotBlank() }

            val specs = mutableListOf<Pair<String, String>>()
            if (specGroup3.isNotBlank()) {
                val splitSpecs = specGroup3.split(",")
                for (s in splitSpecs) {
                    val sTrim = s.trim()
                    val opMatch = Regex("^(>=|<=|==|~=|!=|<|>)\\s*([0-9a-zA-Z.-]+)$").find(sTrim)
                    if (opMatch != null) {
                        specs.add(opMatch.groupValues[1] to opMatch.groupValues[2])
                    }
                }
            } else if (specGroup4.isNotBlank() && specGroup5.isNotBlank()) {
                specs.add(specGroup4 to specGroup5)
            }
            
            return Requirement(name, extras, specs, marker)
        }

        fun checkVersion(version: String, specs: List<Pair<String, String>>): Boolean {
            if (specs.isEmpty()) return true
            val v = Version(version)
            for (spec in specs) {
                val specVersion = Version(spec.second)
                val ok = when (spec.first) {
                    ">=" -> v >= specVersion
                    "<=" -> v <= specVersion
                    "==" -> v == specVersion
                    "!=" -> v != specVersion
                    ">" -> v > specVersion
                    "<" -> v < specVersion
                    "~=" -> v.parts.getOrElse(0) {0} == specVersion.parts.getOrElse(0) {0} && v >= specVersion
                    else -> true
                }
                if (!ok) return false
            }
            return true
        }

        fun evaluateMarker(marker: String?, requestedExtras: List<String>): Boolean {
            if (marker == null) return true
            var m = marker.replace("\"", "'")
            
            val pyVerParts = context.pythonVersion.split(".")
            val pyVer = "${pyVerParts.getOrElse(0){"3"}}.${pyVerParts.getOrElse(1){"10"}}"

            m = m.replace("sys_platform", "'android'")
            m = m.replace("platform_system", "'Android'")
            m = m.replace("python_version", "'$pyVer'")
            m = m.replace("os_name", "'posix'")

            if (m.contains("extra == ")) {
                val extraMatch = Regex("extra == '([^']+)'").find(m)
                if (extraMatch != null) {
                    if (!requestedExtras.contains(extraMatch.groupValues[1])) {
                        return false
                    }
                }
            }
            return true
        }

        fun resolveNode(name: String, specs: List<Pair<String, String>>, extras: List<String>) {
            val normalizedName = name.lowercase().replace("-", "_")
            if (processing.contains(normalizedName)) return
            processing.add(normalizedName)

            try {
                if (resolvedPackages.containsKey(normalizedName)) {
                    val existing = resolvedPackages[normalizedName]!!
                    if (!checkVersion(existing.version, specs)) {
                        throw Exception("Version conflict for $name: resolved ${existing.version} but requires $specs")
                    }
                    return
                }

                val packageData = fetchPackageData(name)
                val info = packageData.getJSONObject("info")
                val version = info.getString("version")

                var targetVersion = version
                var targetInfo = info
                var targetData = packageData

                if (!checkVersion(version, specs)) {
                    val releases = packageData.getJSONObject("releases")
                    var bestVersion: String? = null
                    for (ver in releases.keys()) {
                        if (checkVersion(ver, specs)) {
                            if (bestVersion == null || Version(ver) > Version(bestVersion)) {
                                bestVersion = ver
                            }
                        }
                    }
                    if (bestVersion == null) {
                        throw Exception("No matching version found for $name with specs $specs")
                    }
                    targetVersion = bestVersion
                    targetData = fetchPackageData(name, bestVersion)
                    targetInfo = targetData.getJSONObject("info")
                }

                val urls = targetData.getJSONArray("urls")
                var sha256 = ""
                var wheelTag = ""
                for (i in 0 until urls.length()) {
                    val urlObj = urls.getJSONObject(i)
                    if (urlObj.getString("packagetype") == "bdist_wheel") {
                        val digests = urlObj.getJSONObject("digests")
                        sha256 = digests.optString("sha256", "")
                        val filename = urlObj.getString("filename")
                        wheelTag = filename.substringAfterLast("-", "").substringBefore(".whl")
                        break // pick the first wheel for simplicity
                    } else if (urlObj.getString("packagetype") == "sdist" && sha256.isEmpty()) {
                        val digests = urlObj.getJSONObject("digests")
                        sha256 = digests.optString("sha256", "")
                    }
                }

                val requiresDist = targetInfo.optJSONArray("requires_dist")
                val deps = mutableListOf<String>()

                if (requiresDist != null) {
                    for (i in 0 until requiresDist.length()) {
                        val reqStr = requiresDist.getString(i)
                        val req = parseRequirement(reqStr)
                        if (evaluateMarker(req.marker, extras)) {
                            deps.add(req.name)
                            resolveNode(req.name, req.specs, req.extras)
                        }
                    }
                }

                resolvedPackages[normalizedName] = PackageLockEntry(
                    name = targetInfo.getString("name"),
                    version = targetVersion,
                    source = "pypi_official",
                    sha256 = sha256,
                    wheelTag = wheelTag,
                    signatureVerified = false,
                    dependencies = deps
                )

            } finally {
                processing.remove(normalizedName)
            }
        }
        
        try {
            for ((depName, depVer) in projectToml.dependencies) {
                val reqStr = if (depVer.isNotBlank() && depVer != "*") "$depName $depVer" else depName
                val req = parseRequirement(reqStr)
                resolveNode(req.name, req.specs, req.extras)
            }
            
            val lockfile = PystudioLock(
                pythonTarget = projectToml.requiresPython,
                resolutionContext = context,
                packages = resolvedPackages.values.toList()
            )
            
            cache.storeL5Resolution(tomlHash, lockfile)
            ResolutionOutcome.Success(lockfile)
        } catch (e: Exception) {
            Log.e("DependencyResolver", "Resolution failed", e)
            ResolutionOutcome.Conflict(e.message ?: "Unknown conflict")
        }
    }
}
