package com.pystudio.core.packages

import java.util.Date

enum class Abi(val tag: String) {
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86_64("x86_64")
}

data class PystudioToml(
    val projectName: String,
    val requiresPython: String,
    val dependencies: Map<String, String>,
    val devDependencies: Map<String, String> = emptyMap(),
    val allowUnsignedLocalBuild: Boolean = true,
    val requireDeveloperSignature: Boolean = false
)

data class ResolutionContext(
    val abi: Abi,
    val apiLevel: Int,
    val pythonVersion: String
)

data class PystudioLock(
    val lockVersion: Int = 1,
    val generatedAt: Date = Date(),
    val pythonTarget: String,
    val resolutionContext: ResolutionContext,
    val packages: List<PackageLockEntry>
)

data class PackageLockEntry(
    val name: String,
    val version: String,
    val source: String,
    val sha256: String,
    val wheelTag: String,
    val signatureVerified: Boolean,
    val dependencies: List<String>
)

data class EnvInfo(
    val envId: String,
    val pythonVersion: String,
    val targetAbi: Abi,
    val active: Boolean,
    val createdAt: Date = Date(),
    val lockfileHash: String = ""
)

data class PackageSummary(
    val name: String,
    val version: String,
    val source: String,
    val sizeBytes: Long,
    val signatureVerified: Boolean,
    val sha256: String = "",
    val wheelTag: String = ""
)

data class InstallPlan(
    val toAdd: List<PackageSummary>,
    val toUpdate: List<Pair<PackageSummary, PackageSummary>>,
    val toRemove: List<PackageSummary>
)

sealed class ResolutionOutcome {
    data class Success(val lockfile: PystudioLock) : ResolutionOutcome()
    data class Conflict(val report: String) : ResolutionOutcome()
}

sealed class InstallOutcome {
    data class Success(val plan: InstallPlan, val lockfileChanged: Boolean) : InstallOutcome()
    data class Failure(val errorCode: String, val message: String) : InstallOutcome()
}

data class ArtifactRef(
    val name: String,
    val version: String,
    val fileAbsolutePath: String,
    val sha256: String,
    val signaturePath: String? = null,
    val isLocal: Boolean = false
)

enum class VerificationResult {
    OK,
    FAILED,
    SKIPPED_LOCAL
}
