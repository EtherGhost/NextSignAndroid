package se.cloudsite.nextsign

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.SignatureElement
import se.cloudsite.nextsign.model.ValidationSummary
import se.cloudsite.nextsign.repository.DocumentDownloader
import se.cloudsite.nextsign.repository.DownloadResult
import se.cloudsite.nextsign.repository.LibreSignRepository
import se.cloudsite.nextsign.repository.LoadDocumentsResult
import se.cloudsite.nextsign.repository.SaveSignatureElementResult
import se.cloudsite.nextsign.repository.SignResult
import se.cloudsite.nextsign.repository.SignatureElementsResult
import se.cloudsite.nextsign.repository.ValidateResult
import se.cloudsite.nextsign.ui.about.AboutScreen
import se.cloudsite.nextsign.ui.documentdetail.DocumentDetailDialog
import se.cloudsite.nextsign.ui.documentdetail.MessageDialog
import se.cloudsite.nextsign.ui.documentdetail.SignConfirmDialog
import se.cloudsite.nextsign.ui.documentlist.DocumentListScreen
import se.cloudsite.nextsign.ui.documentlist.SortMode
import se.cloudsite.nextsign.ui.documentlist.sortDocuments
import se.cloudsite.nextsign.ui.settings.SettingsScreen
import se.cloudsite.nextsign.ui.signature.SignatureDrawScreen
import se.cloudsite.nextsign.ui.signature.SignatureSetupScreen
import se.cloudsite.nextsign.ui.theme.NextSignTheme
import se.cloudsite.nextsign.util.SignatureImageEncoder
import se.cloudsite.nextsign.util.ThemeMode
import se.cloudsite.nextsign.util.ThemePreference

private enum class Screen { DOCUMENT_LIST, SIGNATURE_SETUP, SIGNATURE_DRAW, SETTINGS, ABOUT }

// Uses AccountImporter.pickNewAccount()/onActivityResult(), not the newer
// ImportSsoAccount ActivityResultContract shown in the library's current README -
// that class isn't in the released 1.3.4 artifact this app depends on (confirmed by
// inspecting the actual AAR), only on the library's unreleased master branch. This is
// the same pattern the real Nextcloud Notes/Deck apps ship with today.
class MainActivity : ComponentActivity() {

    private val repository by lazy { LibreSignRepository(applicationContext) }
    private val documentDownloader by lazy { DocumentDownloader(applicationContext) }

    private var account: SingleSignOnAccount? by mutableStateOf(null)
    private var documents: List<LibreSignDocument> by mutableStateOf(emptyList())
    private var loading: Boolean by mutableStateOf(false)
    private var errorMessage: String by mutableStateOf("")

    // Tracked by uuid, not by holding the LibreSignDocument instance directly, so the
    // detail dialog reflects fresh canSignNow/signers state after a refresh instead of
    // showing a stale snapshot from before a sign/validate action.
    private var selectedDocumentUuid: String? by mutableStateOf(null)
    private var pendingSignDocument: LibreSignDocument? by mutableStateOf(null)
    private var signingUuid: String? by mutableStateOf(null)
    private var validatingUuid: String? by mutableStateOf(null)
    private var downloadingUuid: String? by mutableStateOf(null)
    private var signSucceededName: String? by mutableStateOf(null)
    private var signErrorMessage: String? by mutableStateOf(null)
    private var validationResult: ValidationSummary? by mutableStateOf(null)
    private var validationErrorMessage: String? by mutableStateOf(null)
    private var downloadErrorMessage: String? by mutableStateOf(null)

    private var currentScreen: Screen by mutableStateOf(Screen.DOCUMENT_LIST)
    private var sortMode: SortMode by mutableStateOf(SortMode.DATE_DESC)
    // { "signature": nodeId, "initial": nodeId, ... } - the account's own registered
    // signature/initials images, needed alongside a document's placeholder position to
    // render a visible mark when signing. Empty until loadSignatureElements() returns.
    private var signatureElementsByType: Map<String, Int> by mutableStateOf(emptyMap())
    private var signaturePreviewBitmap: Bitmap? by mutableStateOf(null)
    private var loadingSignaturePreview: Boolean by mutableStateOf(false)
    private var savingSignatureElement: Boolean by mutableStateOf(false)
    private var signatureSetupError: String by mutableStateOf("")

    private var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handlePickedImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeMode = ThemePreference.get(this)

        account = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(this)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        }

        setContent {
            NextSignTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentAccount = account
                    LaunchedEffect(currentAccount) {
                        if (currentAccount != null) {
                            refresh(currentAccount)
                            loadSignatureElements(currentAccount)
                        }
                    }

                    if (currentAccount == null) {
                        SignInScreen(onSignIn = { pickAccount() })
                    } else {
                        when (currentScreen) {
                            Screen.DOCUMENT_LIST -> {
                                val selectedDocument = documents.find { it.uuid == selectedDocumentUuid }
                                val drawerState = rememberDrawerState(DrawerValue.Closed)
                                val drawerScope = rememberCoroutineScope()

                                ModalNavigationDrawer(
                                    drawerState = drawerState,
                                    drawerContent = {
                                        ModalDrawerSheet {
                                            Text(
                                                "NextSign",
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                            NavigationDrawerItem(
                                                label = { Text("Signature") },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    openSignatureSetup(currentAccount)
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text("Settings") },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    currentScreen = Screen.SETTINGS
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text("About") },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    currentScreen = Screen.ABOUT
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    AppScreen(
                                        account = currentAccount,
                                        documents = sortDocuments(documents, sortMode),
                                        loading = loading,
                                        errorMessage = errorMessage,
                                        selectedDocument = selectedDocument,
                                        signing = signingUuid == selectedDocument?.uuid,
                                        validating = validatingUuid == selectedDocument?.uuid,
                                        downloading = downloadingUuid == selectedDocument?.uuid,
                                        sortMode = sortMode,
                                        onSortModeSelected = { sortMode = it },
                                        onMenuClick = { drawerScope.launch { drawerState.open() } },
                                        onRefresh = { refresh(currentAccount) },
                                        onDocumentClick = { selectedDocumentUuid = it.uuid },
                                        onDismissDetail = { selectedDocumentUuid = null },
                                        onSignClick = { document ->
                                            selectedDocumentUuid = null
                                            attemptSign(currentAccount, document)
                                        },
                                        onValidateClick = { document ->
                                            selectedDocumentUuid = null
                                            validateDocument(currentAccount, document)
                                        },
                                        onOpenFileClick = { document ->
                                            selectedDocumentUuid = null
                                            downloadAndOpen(currentAccount, document)
                                        }
                                    )
                                }

                                pendingSignDocument?.let { document ->
                                    SignConfirmDialog(
                                        documentName = document.name,
                                        onConfirm = {
                                            pendingSignDocument = null
                                            signDocument(currentAccount, document)
                                        },
                                        onDismiss = { pendingSignDocument = null }
                                    )
                                }

                                signSucceededName?.let { name ->
                                    MessageDialog(
                                        title = "Signed",
                                        message = "\"${name.ifEmpty { "Untitled document" }}\" has been signed.",
                                        onDismiss = { signSucceededName = null }
                                    )
                                }

                                signErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = "Could not sign document",
                                        message = message,
                                        onDismiss = { signErrorMessage = null }
                                    )
                                }

                                validationResult?.let { summary ->
                                    MessageDialog(
                                        title = "Signature validation",
                                        message = formatValidationSummary(summary),
                                        onDismiss = { validationResult = null }
                                    )
                                }

                                validationErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = "Could not validate document",
                                        message = message,
                                        onDismiss = { validationErrorMessage = null }
                                    )
                                }

                                downloadErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = "Could not open document",
                                        message = message,
                                        onDismiss = { downloadErrorMessage = null }
                                    )
                                }
                            }

                            Screen.SIGNATURE_SETUP -> SignatureSetupScreen(
                                hasSignature = "signature" in signatureElementsByType,
                                previewBitmap = signaturePreviewBitmap,
                                loadingPreview = loadingSignaturePreview,
                                saving = savingSignatureElement,
                                errorMessage = signatureSetupError,
                                onPickImage = { pickImageLauncher.launch("image/*") },
                                onDrawSignature = { currentScreen = Screen.SIGNATURE_DRAW },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )

                            Screen.SIGNATURE_DRAW -> SignatureDrawScreen(
                                onSave = { dataUri ->
                                    currentScreen = Screen.SIGNATURE_SETUP
                                    signaturePreviewBitmap = SignatureImageEncoder.decodeDataUri(dataUri)
                                    saveSignatureElement(currentAccount, dataUri)
                                },
                                onCancel = { currentScreen = Screen.SIGNATURE_SETUP }
                            )

                            Screen.SETTINGS -> SettingsScreen(
                                themeMode = themeMode,
                                onThemeModeSelected = { mode ->
                                    themeMode = mode
                                    ThemePreference.set(this@MainActivity, mode)
                                },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )

                            Screen.ABOUT -> AboutScreen(onBack = { currentScreen = Screen.DOCUMENT_LIST })
                        }
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

    // Only checks the "signature" image type, not "initial" - clickToSign documents
    // set up so far only ever place a signature field, matching the Ubuntu Touch app.
    private fun needsSignatureSetup(document: LibreSignDocument): Boolean {
        if ("signature" in signatureElementsByType) {
            return false
        }
        return document.visibleElements.any { it.type == "signature" }
    }

    private fun attemptSign(account: SingleSignOnAccount, document: LibreSignDocument) {
        if (needsSignatureSetup(document)) {
            openSignatureSetup(account)
            return
        }
        pendingSignDocument = document
    }

    private fun signDocument(account: SingleSignOnAccount, document: LibreSignDocument) {
        if (document.signUuid.isEmpty()) {
            signErrorMessage = "Could not determine your signature request for this document."
            return
        }
        signingUuid = document.uuid
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.signDocument(account, document.signUuid, document.visibleElements, signatureElementsByType)
            }
            signingUuid = null
            when (result) {
                is SignResult.Success -> {
                    signSucceededName = document.name
                    refresh(account)
                }
                is SignResult.Failure -> signErrorMessage = result.message
            }
        }
    }

    private fun validateDocument(account: SingleSignOnAccount, document: LibreSignDocument) {
        validatingUuid = document.uuid
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.validateDocument(account, document.uuid) }
            validatingUuid = null
            when (result) {
                is ValidateResult.Success -> validationResult = result.summary
                is ValidateResult.Failure -> validationErrorMessage = result.message
            }
        }
    }

    private fun downloadAndOpen(account: SingleSignOnAccount, document: LibreSignDocument) {
        downloadingUuid = document.uuid
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { documentDownloader.downloadDocument(account, document) }
            downloadingUuid = null
            when (result) {
                is DownloadResult.Success -> openFile(result.file, result.mimeType)
                is DownloadResult.Failure -> downloadErrorMessage = result.message
            }
        }
    }

    private fun openFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            downloadErrorMessage = "No app installed can open this document."
        }
    }

    private fun loadSignatureElements(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.loadSignatureElements(account) }) {
                is SignatureElementsResult.Success -> signatureElementsByType = buildSignatureElementsByType(result.elements)
                is SignatureElementsResult.Failure -> { /* Non-fatal - signing just won't offer a visible mark yet. */ }
            }
        }
    }

    private fun openSignatureSetup(account: SingleSignOnAccount) {
        signatureSetupError = ""
        currentScreen = Screen.SIGNATURE_SETUP
        val nodeId = signatureElementsByType["signature"]
        if (nodeId != null && signaturePreviewBitmap == null) {
            loadSignaturePreview(account, nodeId)
        }
    }

    private fun loadSignaturePreview(account: SingleSignOnAccount, nodeId: Int) {
        loadingSignaturePreview = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { documentDownloader.downloadSignaturePreview(account, nodeId) }
            loadingSignaturePreview = false
            when (result) {
                is DownloadResult.Success -> signaturePreviewBitmap = BitmapFactory.decodeFile(result.file.path)
                is DownloadResult.Failure -> { /* Non-fatal - the picker/draw flow still works without a preview. */ }
            }
        }
    }

    private fun handlePickedImage(uri: Uri) {
        val account = this.account ?: return
        val dataUri = SignatureImageEncoder.toBase64DataUri(applicationContext, uri)
        if (dataUri == null) {
            signatureSetupError = "Could not use the selected image. Try a smaller picture."
            return
        }
        signaturePreviewBitmap = SignatureImageEncoder.decodeDataUri(dataUri)
        saveSignatureElement(account, dataUri)
    }

    private fun saveSignatureElement(account: SingleSignOnAccount, dataUri: String) {
        savingSignatureElement = true
        signatureSetupError = ""
        val existingNodeId = signatureElementsByType["signature"]
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.saveSignatureElement(account, "signature", dataUri, existingNodeId)
            }
            savingSignatureElement = false
            when (result) {
                is SaveSignatureElementResult.Success -> signatureElementsByType = buildSignatureElementsByType(result.elements)
                is SaveSignatureElementResult.Failure -> signatureSetupError = result.message
            }
        }
    }
}

// Prefers the starred (default) element of a type over an earlier one.
private fun buildSignatureElementsByType(elements: List<SignatureElement>): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    elements.forEach { element ->
        if (element.type !in map || element.starred) {
            map[element.type] = element.nodeId
        }
    }
    return map
}

@Composable
private fun SortMenuButton(sortMode: SortMode, onSortModeSelected: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(sortMode.label())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        onSortModeSelected(mode)
                    }
                )
            }
        }
    }
}

private fun SortMode.label(): String = when (this) {
    SortMode.DATE_DESC -> "Newest first"
    SortMode.DATE_ASC -> "Oldest first"
    SortMode.NAME_ASC -> "Name (A-Z)"
}

private fun formatValidationSummary(summary: ValidationSummary): String {
    val lines = mutableListOf(summary.statusText.ifEmpty { "Signed" })
    summary.signers.forEach { signer ->
        lines.add("")
        lines.add(signer.displayName.ifEmpty { "Unknown signer" })
        if (signer.signatureLabel.isNotEmpty()) lines.add(signer.signatureLabel)
        if (signer.certificateLabel.isNotEmpty()) lines.add(signer.certificateLabel)
    }
    return lines.joinToString("\n")
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
    signing: Boolean,
    validating: Boolean,
    downloading: Boolean,
    sortMode: SortMode,
    onSortModeSelected: (SortMode) -> Unit,
    onMenuClick: () -> Unit,
    onRefresh: () -> Unit,
    onDocumentClick: (LibreSignDocument) -> Unit,
    onDismissDetail: () -> Unit,
    onSignClick: (LibreSignDocument) -> Unit,
    onValidateClick: (LibreSignDocument) -> Unit,
    onOpenFileClick: (LibreSignDocument) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NextSign") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    SortMenuButton(sortMode = sortMode, onSortModeSelected = onSortModeSelected)
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
                onRefresh = onRefresh,
                onDocumentClick = onDocumentClick
            )
        }
    }

    if (selectedDocument != null) {
        DocumentDetailDialog(
            document = selectedDocument,
            signing = signing,
            validating = validating,
            downloading = downloading,
            onSignClick = { onSignClick(selectedDocument) },
            onValidateClick = { onValidateClick(selectedDocument) },
            onOpenFileClick = { onOpenFileClick(selectedDocument) },
            onDismiss = onDismissDetail
        )
    }
}
