package com.pystudio.core.packages

import android.content.Context
import java.io.File
import java.util.Date

class EnvironmentService(private val context: Context) {
    private val envsDir = File(context.filesDir, "envs").apply { mkdirs() }
    
    private var activeEnvId: String? = null

    suspend fun create(name: String, pythonVersion: String, abi: Abi): EnvInfo {
        val envDir = File(envsDir, name)
        if (!envDir.exists()) envDir.mkdirs()
        File(envDir, "site-packages").mkdirs()
        
        val info = EnvInfo(
            envId = name,
            pythonVersion = pythonVersion,
            targetAbi = abi,
            active = false,
            createdAt = Date()
        )
        File(envDir, "env.json").writeText("{\"env_id\": \"$name\"}")
        return info
    }

    suspend fun activate(envId: String) {
        val envDir = File(envsDir, envId)
        if (envDir.exists()) {
            activeEnvId = envId
        } else {
            throw IllegalArgumentException("Environment $envId does not exist")
        }
    }

    suspend fun delete(envId: String) {
        val envDir = File(envsDir, envId)
        if (envDir.exists()) {
            envDir.deleteRecursively()
        }
        if (activeEnvId == envId) {
            activeEnvId = null
        }
    }

    suspend fun list(): List<EnvInfo> {
        val dirs = envsDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        return dirs.map { 
            EnvInfo(
                envId = it.name,
                pythonVersion = "3.13",
                targetAbi = Abi.ARM64_V8A,
                active = (it.name == activeEnvId)
            )
        }
    }
    
    fun getEnvPath(envId: String): String {
        return File(envsDir, envId).absolutePath
    }
}
