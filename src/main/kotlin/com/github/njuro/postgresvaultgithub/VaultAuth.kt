package com.github.njuro.postgresvaultgithub

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.OneTimeString
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.ApplicabilityLevel
import com.intellij.database.dataSource.DatabaseConnectionConfig
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.ui.AuthWidgetBuilder
import com.intellij.database.dataSource.url.template.MutableParametersHolder
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UnstableApiUsage")
class VaultAuth : DatabaseAuthProvider {
    private val vault = Vault()

    companion object {
        private const val VAULT_ADDRESS = "vault.address"
        private const val VAULT_TOKEN = "vault.token"
        private const val VAULT_PATH = "vault.path"

        private val noUrlHandler = object : AuthWidgetBuilder.UrlHandler<String> {
            override fun fromUrl(holder: ParametersHolder): String = ""
            override fun toUrl(value: String, holder: MutableParametersHolder) {}
        }

        private val noPasswordUrlHandler = object : AuthWidgetBuilder.UrlHandler<OneTimeString> {
            override fun fromUrl(holder: ParametersHolder): OneTimeString = OneTimeString("")
            override fun toUrl(value: OneTimeString, holder: MutableParametersHolder) {}
        }

        private val tokenSerializer = object : AuthWidgetBuilder.Serialiser<OneTimeString> {
            override fun save(value: OneTimeString?, config: DatabaseConnectionConfig, credentials: DatabaseCredentials?) {
                config.dataSource.setAdditionalProperty(VAULT_TOKEN, value?.toString() ?: "")
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

    override fun AuthWidgetBuilder.configureWidget(
        project: Project?,
        credentials: DatabaseCredentials,
        config: DatabaseConnectionConfig
    ) {
        this
            .addTextField(
                { VaultBundle.message("addressLabel") },
                AuthWidgetBuilder.additionalPropertySerializer(VAULT_ADDRESS),
                noUrlHandler
            )
            .addPasswordField(
                { VaultBundle.message("tokenLabel") },
                tokenSerializer,
                false,
                noPasswordUrlHandler
            )
            .addTextField(
                { VaultBundle.message("pathLabel") },
                AuthWidgetBuilder.additionalPropertySerializer(VAULT_PATH),
                noUrlHandler
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
