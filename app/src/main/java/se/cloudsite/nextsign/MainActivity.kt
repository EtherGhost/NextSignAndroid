package se.cloudsite.nextsign

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.repository.LibreSignRepository
import se.cloudsite.nextsign.repository.LoadDocumentsResult
import se.cloudsite.nextsign.ui.documentdetail.DocumentDetailDialog
import se.cloudsite.nextsign.ui.documentlist.DocumentListScreen

// Uses AccountImporter.pickNewAccount()/onActivityResult(), not the newer
// ImportSsoAccount ActivityResultContract shown in the library's current README -
// that class isn't in the released 1.3.4 artifact this app depends on (confirmed by
// inspecting the actual AAR), only on the library's unreleased master branch. This is
// the same pattern the real Nextcloud Notes/Deck apps ship with today.
class MainActivity : ComponentActivity() {

    private val repository by lazy { LibreSignRepository(applicationContext) }

    private var account: SingleSignOnAccount? by mutableStateOf(null)
    private var documents: List<LibreSignDocument> by mutableStateOf(emptyList())
    private var loading: Boolean by mutableStateOf(false)
    private var errorMessage: String by mutableStateOf("")
    private var selectedDocument: LibreSignDocument? by mutableStateOf(null)

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
                    val currentAccount = account
                    LaunchedEffect(currentAccount) {
                        if (currentAccount != null) {
                            refresh(currentAccount)
                        }
                    }

                    if (currentAccount == null) {
                        SignInScreen(onSignIn = { pickAccount() })
                    } else {
                        AppScreen(
                            account = currentAccount,
                            documents = documents,
                            loading = loading,
                            errorMessage = errorMessage,
                            selectedDocument = selectedDocument,
                            onRefresh = { refresh(currentAccount) },
                            onDocumentClick = { selectedDocument = it },
                            onDismissDetail = { selectedDocument = null }
                        )
                    }
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
            errorMessage = "Account import canceled."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun refresh(account: SingleSignOnAccount) {
        loading = true
        errorMessage = ""
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.loadDocuments(account) }) {
                is LoadDocumentsResult.Success -> {
                    documents = result.documents
                    loading = false
                }
                is LoadDocumentsResult.Failure -> {
                    errorMessage = result.message
                    loading = false
                }
            }
        }
    }
}

@Composable
private fun SignInScreen(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "NextSign", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSignIn) {
            Text("Sign in with Nextcloud")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    account: SingleSignOnAccount,
    documents: List<LibreSignDocument>,
    loading: Boolean,
    errorMessage: String,
    selectedDocument: LibreSignDocument?,
    onRefresh: () -> Unit,
    onDocumentClick: (LibreSignDocument) -> Unit,
    onDismissDetail: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NextSign") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Signed in as ${account.name}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            DocumentListScreen(
                documents = documents,
                loading = loading,
                errorMessage = errorMessage,
                onDocumentClick = onDocumentClick
            )
        }
    }

    if (selectedDocument != null) {
        DocumentDetailDialog(document = selectedDocument, onDismiss = onDismissDetail)
    }
}
