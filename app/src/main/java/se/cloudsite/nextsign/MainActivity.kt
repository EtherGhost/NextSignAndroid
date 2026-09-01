package se.cloudsite.nextsign

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import com.nextcloud.android.sso.ui.UiExceptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.cloudsite.nextsign.network.ApiProvider

// Step 1 of the Android port: scaffold + SSO auth + one proven authenticated OCS call.
// The document list, sign/validate, and signature setup screens replace this test
// screen in later steps - see the NextSign Android port plan.
//
// Uses AccountImporter.pickNewAccount()/onActivityResult(), not the newer
// ImportSsoAccount ActivityResultContract shown in the library's current README -
// that class isn't in the released 1.3.4 artifact this app depends on (confirmed by
// inspecting the actual AAR), only on the library's unreleased master branch. This is
// the same pattern the real Nextcloud Notes/Deck apps ship with today.
class MainActivity : ComponentActivity() {

    private var account: SingleSignOnAccount? by mutableStateOf(null)
    private var statusText: String by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(this)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        account = account,
                        statusText = statusText,
                        onSignIn = { pickAccount() },
                        onTestConnection = { runTestConnection() }
                    )
                }
            }
        }
    }

    private fun pickAccount() {
        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: NextcloudFilesAppNotInstalledException) {
            UiExceptionManager.showDialogForException(this, e)
        } catch (e: AndroidGetAccountsPermissionNotGranted) {
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this) { ssoAccount ->
                SingleAccountHelper.commitCurrentAccount(this, ssoAccount.name)
                account = ssoAccount
            }
        } catch (e: AccountImportCancelledException) {
            statusText = "Account import canceled."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun runTestConnection() {
        val currentAccount = account ?: return
        statusText = "Loading..."
        lifecycleScope.launch {
            statusText = withContext(Dispatchers.IO) { testConnection(currentAccount) }
        }
    }

    private fun testConnection(account: SingleSignOnAccount): String {
        return try {
            val api = ApiProvider.getLibreSignApi(applicationContext, account)
            val response = api.listFiles().execute()
            // Retrofit only populates body() for a successful response - an error
            // response (including the SSO library's synthetic HTTP 900 for a non-HTTP
            // failure, see Retrofit2Helper.convertExceptionToResponse) has its content
            // in errorBody() instead. Reading body() unconditionally silently returns
            // null/empty for anything non-2xx, hiding the actual diagnostic text.
            val bodyText = if (response.isSuccessful) {
                response.body()?.toString().orEmpty()
            } else {
                response.errorBody()?.string().orEmpty()
            }
            "HTTP ${response.code()}\n\n$bodyText"
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }
}

@Composable
private fun MainScreen(
    account: SingleSignOnAccount?,
    statusText: String,
    onSignIn: () -> Unit,
    onTestConnection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "NextSign", style = MaterialTheme.typography.headlineMedium)

        if (account == null) {
            Button(onClick = onSignIn) {
                Text("Sign in with Nextcloud")
            }
        } else {
            Text(text = "Signed in as ${account.name}")
            Button(onClick = onTestConnection) {
                Text("Test connection")
            }
        }

        if (statusText.isNotEmpty()) {
            Text(text = statusText)
        }
    }
}
