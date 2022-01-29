package com.github.njuro.postgresvaultgithub

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.IOException

internal class Vault {
    private companion object {
        private val mapper = jacksonObjectMapper()
        private val vaultExec get() = findExecutable("vault")

        private val executableSearchPaths = listOf(
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
            "/usr/sbin",
            "/sbin",
            "/opt/homebrew/bin",
            System.getProperty("user.home") + "/bin"
        )

        private fun findExecutable(exec: String) =
            executableSearchPaths
                .firstOrNull { File(it, exec).exists() }
                ?.let { "$it/$exec" }
                ?: exec
    }

    fun readJson(path: String, address: String, token: String): ObjectNode {
        authenticate(address, token)
        return executeAndReturnJson(vaultExec, "read", "-address=$address", "-format=json", path)
    }

    private fun authenticate(address: String, token: String) {
        if (isAuthenticated(address)) return
        executeAndReturnJson(vaultExec, "login", "-address=$address", "-method=github", "-format=json", "token=$token")
    }

    private fun isAuthenticated(address: String): Boolean =
        execute(vaultExec, "token", "lookup", "-address=$address") {
            val errorText = it.errorStream.bufferedReader().readText()
            if (it.exitValue() != 0 && !errorText.contains("permission denied")) {
                throw IOException(VaultBundle.message("authenticationFailed", errorText))
            }
            it.exitValue() == 0
        }

    private fun <R> execute(vararg command: String, onSuccess: (Process) -> R) =
        execute(ProcessBuilder(*command), onSuccess)

    private fun executeAndReturnJson(vararg command: String) =
        execute(ProcessBuilder(*command)) {
            if (it.exitValue() != 0) {
                val errorText = it.errorStream.bufferedReader().readText()
                throw IOException(VaultBundle.message("processFailed", command, errorText))
            }
            mapper.readValue(it.inputStream, ObjectNode::class.java)
        }

    private fun <R> execute(pb: ProcessBuilder, onSuccess: (Process) -> R) =
        try {
            pb.start()
        } catch (err: IOException) {
            throw IOException(
                VaultBundle.message(
                    "processFailed",
                    pb.command().joinToString(separator = " "),
                    err.message ?: ""
                )
            )
        }.also { it.waitFor() }.let(onSuccess)
}
