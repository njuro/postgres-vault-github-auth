package com.github.njuro.postgresvaultgithub

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.OneTimeString
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthParameterBuilderHelper
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.ApplicabilityLevel
import com.intellij.database.dataSource.DatabaseAuthProviderParam
import com.intellij.database.dataSource.DatabaseConnectionConfig
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.url.template.MutableParametersHolder
import com.intellij.database.dataSource.url.template.ParametersHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UnstableApiUsage")
class VaultAuth : DatabaseAuthProvider {
    private val vault = Vault()

    companion object {
        private const val VAULT_ADDRESS = "vault.address"
        private const val VAULT_TOKEN = "vault.token"
        private const val VAULT_PATH = "vault.path"

        private val noUrlHandler = object : DatabaseAuthParameterBuilderHelper.UrlHandler<String?> {
            override fun fromUrl(holder: ParametersHolder): String = ""
            override fun toUrl(v: String?, model: MutableParametersHolder) {}
        }

        private val noPasswordUrlHandler = object : DatabaseAuthParameterBuilderHelper.UrlHandler<OneTimeString?> {
            override fun fromUrl(holder: ParametersHolder): OneTimeString = OneTimeString("")
            override fun toUrl(v: OneTimeString?, model: MutableParametersHolder) {}
        }

        private val tokenSerializer = object : DatabaseAuthParameterBuilderHelper.Serialiser<OneTimeString?> {
            override fun save(
                v: OneTimeString?,
                config: DatabaseConnectionConfig,
                credentials: DatabaseCredentials?
            ) {
                config.dataSource.setAdditionalProperty(VAULT_TOKEN, v?.toString() ?: "")
            }

            override fun load(point: DatabaseConnectionPoint, credentials: DatabaseCredentials?): OneTimeString {
                return OneTimeString(point.dataSource.getAdditionalProperty(VAULT_TOKEN) ?: "")
            }
        }
    }

    override fun getId() = "vault"

    override fun getDisplayName() = VaultBundle.message("name")

    override fun getApplicability(
        point: DatabaseConnectionPoint,
        level: ApplicabilityLevel
    ): ApplicabilityLevel.Result = ApplicabilityLevel.Result.APPLICABLE

    override fun getAuthParams(config: DatabaseConnectionConfig): List<DatabaseAuthProviderParam<*>> =
        DatabaseAuthProviderParam.buildParams {
            textParam(
                VAULT_ADDRESS,
                VaultBundle.message("addressLabel"),
                serializer = DatabaseAuthParameterBuilderHelper.additionalPropertySerializer(VAULT_ADDRESS),
                urlHandler = noUrlHandler
            )
            passwordParam(
                VAULT_TOKEN,
                VaultBundle.message("tokenLabel"),
                serializer = tokenSerializer,
                urlHandler = noPasswordUrlHandler
            )
            textParam(
                VAULT_PATH,
                VaultBundle.message("pathLabel"),
                serializer = DatabaseAuthParameterBuilderHelper.additionalPropertySerializer(VAULT_PATH),
                urlHandler = noUrlHandler
            )
        }

    override suspend fun interceptConnection(proto: ProtoConnection, silent: Boolean): Boolean {
        val mountPath = proto.connectionPoint.getAdditionalProperty(VAULT_PATH)
            ?: throw VaultAuthException(VaultBundle.message("invalidMountPath"))
        val addressPath = proto.connectionPoint.getAdditionalProperty(VAULT_ADDRESS)
            ?: throw VaultAuthException(VaultBundle.message("invalidAddressPath"))
        val tokenPath = proto.connectionPoint.getAdditionalProperty(VAULT_TOKEN)
            ?: throw VaultAuthException(VaultBundle.message("invalidTokenPath"))

        val json = try {
            withContext(Dispatchers.IO) {
                vault.readJson(mountPath, addressPath, tokenPath)
            }
        } catch (err: JsonProcessingException) {
            throw VaultAuthException(VaultBundle.message("jsonError"), err)
        }

        val username = json.path("data").path("username").asText()
        val password = json.path("data").path("password").asText()

        if (username.isEmpty() || password.isEmpty()) {
            throw VaultAuthException(VaultBundle.message("invalidResponse"))
        }

        DatabaseCredentialsAuthProvider.applyCredentials(proto, Credentials(username, password), true)
        return true
    }

    internal class VaultAuthException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
}
