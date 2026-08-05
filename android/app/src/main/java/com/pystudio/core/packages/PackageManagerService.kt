package com.pystudio.core.packages

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.File

// S-8: Gestion des packages Python (Facade / CliDispatcher)
class PackageManagerService(private val context: Context, private val pythonHome: String) {

    private val cacheService = UnifiedCacheService(context)
    private val environmentService = EnvironmentService(context)
    private val securityGateService = SecurityGateService(context)
    private val dependencyResolver = DependencyResolverService(cacheService)
    private val installService = PackageInstallService(cacheService, securityGateService, environmentService, pythonHome)

    init {
        runBlocking {
            val defaultEnv = "default"
            if (!File(environmentService.getEnvPath(defaultEnv)).exists()) {
                environmentService.create(defaultEnv, "3.13", Abi.ARM64_V8A)
            }
            environmentService.activate(defaultEnv)
        }
    }

    // Commande unifiée py install
    fun installPackage(packageName: String, envPath: String? = null): Boolean {
        return runBlocking {
            val envId = envPath?.let { File(it).name } ?: "default"
            
            val toml = PystudioToml(
                projectName = "MyProject",
                requiresPython = ">=3.13",
                dependencies = mapOf(packageName to "*")
            )
            
            val contextInfo = ResolutionContext(Abi.ARM64_V8A, 34, "3.13")
            
            when (val resolution = dependencyResolver.resolve(toml, contextInfo)) {
                is ResolutionOutcome.Success -> {
                    val plan = InstallPlan(
                        toAdd = resolution.lockfile.packages.map {
                            PackageSummary(it.name, it.version, it.source, 0L, it.signatureVerified, it.sha256, it.wheelTag)
                        },
                        toUpdate = emptyList(),
                        toRemove = emptyList()
                    )
                    
                    val result = installService.install(plan, envId)
                    result is InstallOutcome.Success
                }
                is ResolutionOutcome.Conflict -> {
                    false
                }
            }
        }
    }
    
    fun uninstallPackage(packageName: String, envPath: String? = null): Boolean {
        return runBlocking {
            val envId = envPath?.let { File(it).name } ?: "default"
            val result = installService.uninstall(packageName, envId)
            result is InstallOutcome.Success
        }
    }
}
