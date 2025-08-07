package com.github.njuro.postgresvaultgithub

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.credentialStore.Credentials
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


    override val coroutineContext = SupervisorJob() + Dispatchers.IO + CoroutineName("VaultAuth")
    override fun getId() = "vault"

    override fun getDisplayName() = VaultBundle.message("name")

    override fun getApplicability(
        point: DatabaseConnectionPoint,
        level: ApplicabilityLevel
    ): ApplicabilityLevel.Result = ApplicabilityLevel.Result.APPLICABLE

    @Deprecated("Use configureWidget() instead")
    override fun createWidget(
        project: Project?,
        credentials: DatabaseCredentials,
        config: DatabaseConnectionConfig
    ): DatabaseAuthProvider.AuthWidget = VaultWidget()

    @Deprecated("Use coroutines", replaceWith = ReplaceWith("interceptConnection"))
    override fun intercept(proto: ProtoConnection, silent: Boolean): CompletionStage<ProtoConnection> {
        val mountPath = proto.connectionPoint.getAdditionalProperty(VAULT_PATH)
            ?: throw VaultAuthException(VaultBundle.message("invalidMountPath"))
        val addressPath = proto.connectionPoint.getAdditionalProperty(VAULT_ADDRESS)
            ?: throw VaultAuthException(VaultBundle.message("invalidAddressPath"))
        val tokenPath = proto.connectionPoint.getAdditionalProperty(VAULT_TOKEN)
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
                proto,
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

        override fun onChanged(r: Runnable) {}

        override fun save(config: DatabaseConnectionConfig, copyCredentials: Boolean) {
            with(config.dataSource) {
                setAdditionalProperty(VAULT_PATH, pathField.text)
                setAdditionalProperty(VAULT_ADDRESS, addressField.text)
                setAdditionalProperty(VAULT_TOKEN, String(tokenField.password))
            }
        }

        override fun reset(point: DatabaseConnectionPoint, resetCredentials: Boolean) {
            with(point.dataSource) {
                pathField.text = getAdditionalProperty(VAULT_PATH) ?: ""
                addressField.text = getAdditionalProperty(VAULT_ADDRESS) ?: ""
                tokenField.text = getAdditionalProperty(VAULT_TOKEN) ?: ""
            }
        }

        override fun updateUrl(model: MutableParametersHolder) {}

        override fun updateFromUrl(holder: ParametersHolder) {}

        override fun isPasswordChanged(): Boolean = false

        override fun hidePassword() {}

        override fun reloadCredentials() {}

        override fun getComponent() = panel

        override fun getPreferredFocusedComponent() = pathField

        override fun forceSave() {}

    }

    internal class VaultAuthException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
}
