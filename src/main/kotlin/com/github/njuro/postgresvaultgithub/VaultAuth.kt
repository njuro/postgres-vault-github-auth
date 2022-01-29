package com.github.njuro.postgresvaultgithub

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.application.ApplicationThreadPool
import com.intellij.credentialStore.Credentials
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.MutableParametersHolder
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel.createLabelConstraints
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel.createSimpleConstraints
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridLayoutManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class VaultAuth : DatabaseAuthProvider, CoroutineScope {
    private val vault = Vault()

    companion object {
        private const val VAULT_ADDRESS = "vault.address"
        private const val VAULT_TOKEN = "vault.token"
        private const val VAULT_PATH = "vault.path"
    }


    override val coroutineContext = SupervisorJob() + Dispatchers.ApplicationThreadPool + CoroutineName("VaultAuth")
    override fun getId() = "vault"

    override fun getDisplayName() = VaultBundle.message("name")

    override fun isApplicable(dataSource: LocalDataSource) =
        dataSource.dbms.isPostgres

    override fun createWidget(
        project: Project?,
        credentials: DatabaseCredentials,
        dataSource: LocalDataSource
    ): DatabaseAuthProvider.AuthWidget {
        return VaultWidget()
    }

    override fun intercept(connection: ProtoConnection, silent: Boolean): CompletionStage<ProtoConnection> {
        val mountPath = connection.connectionPoint.additionalProperties[VAULT_PATH]
            ?: throw VaultAuthException(VaultBundle.message("invalidMountPath"))
        val addressPath = connection.connectionPoint.additionalProperties[VAULT_ADDRESS]
            ?: throw VaultAuthException(VaultBundle.message("invalidAddressPath"))
        val tokenPath = connection.connectionPoint.additionalProperties[VAULT_TOKEN]
            ?: throw VaultAuthException(VaultBundle.message("invalidTokenPath"))

        return future {
            val json = try {
                vault.readJson(mountPath, addressPath, tokenPath)
            } catch (err: JsonProcessingException) {
                throw VaultAuthException(VaultBundle.message("jsonError"), err)
            }

            val username = json.path("data").path("username").asText()
            val password = json.path("data").path("password").asText()

            if (username.isEmpty() || password.isEmpty()) {
                throw VaultAuthException(VaultBundle.message("invalidResponse"))
            }

            DatabaseCredentialsAuthProvider.applyCredentials(
                connection,
                Credentials(username, password),
                true
            )
        }
    }

    @Suppress("TooManyFunctions", "EmptyFunctionBlock", "MagicNumber")
    private class VaultWidget : DatabaseAuthProvider.AuthWidget {
        private val addressField = JBTextField()
        private val tokenField = JBPasswordField()
        private val pathField = JBTextField()
        private val panel = JPanel(GridLayoutManager(3, 6)).apply {
            val addressLabel = JBLabel(VaultBundle.message("addressLabel"))
            add(addressLabel, createLabelConstraints(0, 0, addressLabel.preferredSize.getWidth()))
            add(addressField, createSimpleConstraints(0, 1, 3))

            val tokenLabel = JBLabel(VaultBundle.message("tokenLabel"))
            add(tokenLabel, createLabelConstraints(1, 0, tokenLabel.preferredSize.getWidth()))
            add(tokenField, createSimpleConstraints(1, 1, 3))

            val pathLabel = JBLabel(VaultBundle.message("pathLabel"))
            add(pathLabel, createLabelConstraints(2, 0, pathLabel.preferredSize.getWidth()))
            add(pathField, createSimpleConstraints(2, 1, 3))
        }

        override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
            dataSource.additionalProperties[VAULT_PATH] = pathField.text
            dataSource.additionalProperties[VAULT_ADDRESS] = addressField.text
            dataSource.additionalProperties[VAULT_TOKEN] = String(tokenField.password)
        }

        override fun reset(dataSource: LocalDataSource, copyCredentials: Boolean) {
            pathField.text = (dataSource.additionalProperties[VAULT_PATH] ?: "")
            addressField.text = (dataSource.additionalProperties[VAULT_ADDRESS] ?: "")
            tokenField.text = (dataSource.additionalProperties[VAULT_TOKEN] ?: "")
        }


        override fun updateUrl(holder: MutableParametersHolder) {}

        override fun updateFromUrl(p0: ParametersHolder) {}

        override fun isPasswordChanged(): Boolean {
            return false
        }

        override fun hidePassword() {
        }

        override fun reloadCredentials() {
        }

        override fun getComponent() = panel

        override fun getPreferredFocusedComponent() = pathField

        override fun forceSave() {
        }

    }

    internal class VaultAuthException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
}
