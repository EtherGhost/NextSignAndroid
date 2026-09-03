package se.cloudsite.nextsign

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.SignatureElement
import se.cloudsite.nextsign.model.ValidationSummary
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.push.DocumentPollWorker
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
import se.cloudsite.nextsign.util.AccountHistory
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.PushPreference
import se.cloudsite.nextsign.util.SeenDocumentsStore
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
    private var signInErrorMessage: String by mutableStateOf("")
    private var showInstallNextcloudButton: Boolean by mutableStateOf(false)
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
    private var showAccountSwitcher: Boolean by mutableStateOf(false)
    private var sortMode: SortMode by mutableStateOf(SortMode.DATE_DESC)
    // { "signature": nodeId, "initial": nodeId, ... } - the account's own registered
    // signature/initials images, needed alongside a document's placeholder position to
    // render a visible mark when signing. Empty until loadSignatureElements() returns.
    private var signatureElementsByType: Map<String, Int> by mutableStateOf(emptyMap())
    private var avatarBitmap: Bitmap? by mutableStateOf(null)
    private var signaturePreviewBitmap: Bitmap? by mutableStateOf(null)
    private var loadingSignaturePreview: Boolean by mutableStateOf(false)
    private var savingSignatureElement: Boolean by mutableStateOf(false)
    private var signatureSetupError: String by mutableStateOf("")

    private var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var notificationMode: NotificationMode by mutableStateOf(NotificationMode.INSTANT)

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handlePickedImage(uri)
        }
    }

    // Only needed for the push-notification test to show a system notification on
    // Android 13+ (POST_NOTIFICATIONS) - the rest of the app doesn't post any.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result observed via ContextCompat when actually posting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeMode = ThemePreference.get(this)
        notificationMode = PushPreference.getMode(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Tier 1 of the push notifications plan - a periodic-sync fallback that works
        // regardless of whether real-time push (Tier 2, below) is available. Runs in
        // both BACKGROUND_ONLY and INSTANT modes; SeenDocumentsStore is what prevents
        // the two tiers from ever producing a duplicate notification for the same
        // document when both are active.
        if (notificationMode != NotificationMode.OFF) {
            DocumentPollWorker.enqueue(this)
        }

        account = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(this)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        }
        // Backfills the switcher's known-accounts list with whatever account was
        // already active before this feature existed (or after a fresh install where
        // the Files app already had a committed account) - otherwise it would only
        // ever contain accounts explicitly (re-)picked after this point.
        account?.let { AccountHistory.remember(this, it.name) }

        setContent {
            NextSignTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentAccount = account
                    LaunchedEffect(currentAccount) {
                        if (currentAccount != null) {
                            refresh(currentAccount)
                            loadSignatureElements(currentAccount)
                            loadAvatar(currentAccount)
                            if (notificationMode == NotificationMode.INSTANT) {
                                syncPushRegistration(currentAccount)
                            }
                        }
                    }

                    if (currentAccount == null) {
                        SignInScreen(
                            errorMessage = signInErrorMessage,
                            showInstallNextcloudButton = showInstallNextcloudButton,
                            onSignIn = { pickAccount() },
                            onInstallNextcloud = { openPlayStoreListing(this@MainActivity, "com.nextcloud.client") }
                        )
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
                                                stringResource(R.string.app_name),
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.drawer_account)) },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    showAccountSwitcher = true
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.drawer_signature)) },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    openSignatureSetup(currentAccount)
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.drawer_settings)) },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    currentScreen = Screen.SETTINGS
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.drawer_about)) },
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
                                        avatarBitmap = avatarBitmap,
                                        onSwitchAccount = { showAccountSwitcher = true },
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
                                        title = stringResource(R.string.signed_dialog_title),
                                        message = stringResource(
                                            R.string.signed_dialog_message,
                                            name.ifEmpty { stringResource(R.string.document_untitled) }
                                        ),
                                        onDismiss = { signSucceededName = null }
                                    )
                                }

                                signErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = stringResource(R.string.sign_error_dialog_title),
                                        message = message,
                                        onDismiss = { signErrorMessage = null }
                                    )
                                }

                                validationResult?.let { summary ->
                                    MessageDialog(
                                        title = stringResource(R.string.validation_result_dialog_title),
                                        message = formatValidationSummary(summary),
                                        onDismiss = { validationResult = null }
                                    )
                                }

                                validationErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = stringResource(R.string.validation_error_dialog_title),
                                        message = message,
                                        onDismiss = { validationErrorMessage = null }
                                    )
                                }

                                downloadErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = stringResource(R.string.open_file_error_dialog_title),
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
                                notificationMode = notificationMode,
                                onNotificationModeSelected = { onNotificationModeChanged(it) },
                                // Live device state, not app state - re-checked each time
                                // Settings is shown rather than cached, so installing ntfy
                                // and coming back immediately reflects it with no extra
                                // signal needed.
                                hasPushDistributor = UnifiedPush.getDistributors(this@MainActivity).isNotEmpty(),
                                onInstallPushHelper = { openPlayStoreListing(this@MainActivity, "io.heckel.ntfy") },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )

                            Screen.ABOUT -> AboutScreen(onBack = { currentScreen = Screen.DOCUMENT_LIST })
                        }

                        if (showAccountSwitcher) {
                            AccountSwitcherDialog(
                                knownAccountNames = AccountHistory.list(this@MainActivity),
                                currentAccountName = currentAccount.name,
                                onSelectAccount = { name ->
                                    showAccountSwitcher = false
                                    if (name != currentAccount.name) {
                                        switchToKnownAccount(name)
                                    }
                                },
                                onAddAccount = {
                                    showAccountSwitcher = false
                                    pickAccount()
                                },
                                onDismiss = { showAccountSwitcher = false }
                            )
                        }
                    }
                }
            }
        }
    }

    // Switches to an account already approved in this app before, with no Files-app
    // UI at all - commitCurrentAccount()/getCurrentSingleSignOnAccount() are a purely
    // local lookup of the account's already-granted token, unlike
    // AccountImporter.pickNewAccount(), which always re-runs the full approval flow
    // even for an account that's already been granted. Matches the real Nextcloud
    // Notes app's own account-switching pattern (ManageAccountsViewModel.java).
    private fun switchToKnownAccount(accountName: String) {
        val previousAccount = account
        SingleAccountHelper.commitCurrentAccount(this, accountName)
        val newAccount = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(this)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        }
        if (newAccount == null) {
            errorMessage = getString(R.string.account_switch_failed)
            return
        }
        if (notificationMode == NotificationMode.INSTANT && previousAccount != null && previousAccount.name != newAccount.name) {
            unregisterWebPushOnly(previousAccount)
        }
        ApiProvider.invalidate(newAccount)
        documents = emptyList()
        signatureElementsByType = emptyMap()
        signaturePreviewBitmap = null
        avatarBitmap = null
        selectedDocumentUuid = null
        errorMessage = ""
        account = newAccount
    }

    private fun pickAccount() {
        signInErrorMessage = ""
        showInstallNextcloudButton = false
        try {
            AccountImporter.pickNewAccount(this)
        } catch (e: NextcloudFilesAppNotInstalledException) {
            // Deliberately not UiExceptionManager.showDialogForException(this, e) here -
            // that builds a MaterialAlertDialogBuilder, which requires the Activity's
            // theme to be Theme.MaterialComponents (or a descendant). This app's real
            // theme (themes.xml) is android:Theme.Material.Light.NoActionBar - the
            // platform theme, not Material Components, since theming is otherwise done
            // entirely in Compose - so that dialog throws IllegalArgumentException
            // immediately on construction. This was a real, confirmed crash: worked on
            // a phone with Nextcloud installed (this path never ran), crashed
            // immediately on one without it (this path always ran, straight into the
            // theme-mismatch exception). Plain Compose state instead, same as every
            // other error message in this app.
            signInErrorMessage = getString(R.string.sign_in_error_no_nextcloud)
            showInstallNextcloudButton = true
        } catch (e: AndroidGetAccountsPermissionNotGranted) {
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this)
        } catch (e: ActivityNotFoundException) {
            // AccountImporter.pickNewAccount() has already confirmed the Nextcloud app
            // is installed by this point, but its own source calls
            // startActivityForResult() on the system account-chooser intent with no
            // try/catch of its own - if that intent doesn't resolve on this device
            // (confirmed via the library's real source, not guessed), it throws this
            // completely uncaught, crashing the app. Real crash report: worked on one
            // phone, crashed on another.
            signInErrorMessage = getString(R.string.sign_in_error_picker_failed)
        } catch (e: Exception) {
            // Catch-all so an unexpected failure here shows a message instead of
            // crashing - this is the very first thing a new user does with the app.
            signInErrorMessage = getString(R.string.sign_in_error_generic, e.message ?: e.toString())
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this) { ssoAccount ->
                val previousAccount = account
                SingleAccountHelper.commitCurrentAccount(this, ssoAccount.name)
                // ApiProvider caches NextcloudAPI/Retrofit instances keyed only by
                // account name - if this same account was picked before (in this app
                // process) and the Files app has since issued a fresh token for it
                // (e.g. after being re-selected), the cached instance would still hold
                // the old, now-mismatched token and every call would fail with
                // TokenMismatchException. Force a fresh instance built from this
                // specific, just-imported SingleSignOnAccount every time.
                ApiProvider.invalidate(ssoAccount)
                AccountHistory.remember(this, ssoAccount.name)
                if (notificationMode == NotificationMode.INSTANT && previousAccount != null && previousAccount.name != ssoAccount.name) {
                    unregisterWebPushOnly(previousAccount)
                }
                // Clear everything account-specific before switching - otherwise the
                // previous account's documents/avatar/signature could stay visible for
                // a moment under the new account's header, or a stale in-flight
                // response for the old account could land after switching (guarded
                // separately in refresh()/loadSignatureElements()/loadAvatar() below).
                documents = emptyList()
                signatureElementsByType = emptyMap()
                signaturePreviewBitmap = null
                avatarBitmap = null
                selectedDocumentUuid = null
                errorMessage = ""
                account = ssoAccount
            }
        } catch (e: AccountImportCancelledException) {
            errorMessage = getString(R.string.account_import_canceled)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    // Tapping a notification (real-time push or the Tier 1 background poll) opens
    // MainActivity - since it's launchMode="singleTask" (see the manifest), if the app
    // is already running this calls onNewIntent() on the existing instance instead of
    // creating a new one or just resuming a stale one. A plain resume wouldn't
    // refresh anything on its own (LaunchedEffect only fires on first composition or
    // an account change) - since true per-document deep-linking isn't feasible (see
    // PushServiceImpl), an immediate refresh is the honest substitute: whatever
    // prompted the notification, the list the user lands on is current.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        account?.let { refresh(it) }
    }

    private fun refresh(account: SingleSignOnAccount) {
        loading = true
        errorMessage = ""
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.loadDocuments(account) }
            // Guards against a stale response landing after the account was switched
            // mid-flight - this.account is the currently active one, `account` is the
            // one this specific call was made for.
            if (account !== this@MainActivity.account) return@launch
            when (result) {
                is LoadDocumentsResult.Success -> {
                    documents = result.documents
                    loading = false
                    // Seeing the list in the app counts the same as being notified
                    // about it - keeps DocumentPollWorker (Tier 1) from later treating
                    // something the user already saw here as a new arrival.
                    SeenDocumentsStore.markSeen(applicationContext, result.documents.map { it.uuid }.toSet())
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
            signErrorMessage = getString(R.string.sign_error_no_sign_uuid)
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
            startActivity(Intent.createChooser(intent, getString(R.string.open_with_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            downloadErrorMessage = getString(R.string.open_file_no_app)
        }
    }

    private fun loadSignatureElements(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.loadSignatureElements(account) }
            if (account !== this@MainActivity.account) return@launch
            when (result) {
                is SignatureElementsResult.Success -> signatureElementsByType = buildSignatureElementsByType(result.elements)
                is SignatureElementsResult.Failure -> { /* Non-fatal - signing just won't offer a visible mark yet. */ }
            }
        }
    }

    private fun loadAvatar(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { documentDownloader.downloadAvatar(account) }
            if (account !== this@MainActivity.account) return@launch
            when (result) {
                is DownloadResult.Success -> avatarBitmap = BitmapFactory.decodeFile(result.file.path)
                is DownloadResult.Failure -> { /* Non-fatal - falls back to the initial-letter avatar. */ }
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
            signatureSetupError = getString(R.string.signature_setup_error_bad_image)
            return
        }
        signaturePreviewBitmap = SignatureImageEncoder.decodeDataUri(dataUri)
        saveSignatureElement(account, dataUri)
    }

    // Push notifications (Tier 2 of the push notifications plan) - fetches the
    // server's VAPID public key and registers this device with whatever UnifiedPush
    // distributor the user has installed (e.g. ntfy). Nextcloud's own webpush
    // handshake requires the VAPID key be passed to UnifiedPush.register() up front,
    // matching Nextcloud Talk's real Android client rather than the lazy
    // VAPID_REQUIRED retry shown in UnifiedPush's own generic example app - Nextcloud
    // always requires VAPID, so there's no reason to wait for that failure first.
    private fun syncPushRegistration(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            try {
                val api = withContext(Dispatchers.IO) { ApiProvider.getNotificationsApi(applicationContext, account) }
                val response = withContext(Dispatchers.IO) { api.getVapidKey().execute() }
                val vapid = response.body()?.ocs?.data?.vapid
                android.util.Log.i(
                    "NextSignPush",
                    "getVapidKey: HTTP ${response.code()}, isSuccessful=${response.isSuccessful}, " +
                        "vapidPresent=${!vapid.isNullOrEmpty()}"
                )
                if (response.isSuccessful && !vapid.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        // saveDistributor() (here via the higher-level tryUseDefaultDistributor)
                        // must happen before register() - confirmed from the connector library's
                        // own source, not obvious from Talk's registration code alone, which only
                        // calls register() because it saves the distributor separately, earlier,
                        // from its own Settings flow.
                        UnifiedPush.tryUseDefaultDistributor(this@MainActivity) { success ->
                            android.util.Log.i("NextSignPush", "tryUseDefaultDistributor: success=$success")
                            if (success) {
                                UnifiedPush.register(this@MainActivity, instance = "default", vapid = vapid)
                                android.util.Log.i("NextSignPush", "UnifiedPush.register() call returned")
                            }
                        }
                    }
                } else {
                    android.util.Log.w("NextSignPush", "No VAPID key available (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                android.util.Log.e("NextSignPush", "Failed to fetch VAPID key", e)
            }
        }
    }

    // Server-side only - tells Nextcloud to stop sending this account's notifications
    // to this device's UnifiedPush endpoint, without tearing down the endpoint itself
    // (see switchToKnownAccount()/onActivityResult(), which reuse the same endpoint
    // for whichever account becomes active next). Fire-and-forget: nothing in the UI
    // depends on this completing, and if it fails, the worst case is a stale
    // subscription that a later full toggle-off will still catch.
    private fun unregisterWebPushOnly(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            try {
                val api = withContext(Dispatchers.IO) { ApiProvider.getNotificationsApi(applicationContext, account) }
                val response = withContext(Dispatchers.IO) { api.unregisterWebPush().execute() }
                android.util.Log.i("NextSignPush", "unregisterWebPush (switch) for ${account.name}: HTTP ${response.code()}")
            } catch (e: Exception) {
                android.util.Log.e("NextSignPush", "unregisterWebPush (switch) failed", e)
            }
        }
    }

    // Full teardown of Tier 2 - unlike unregisterWebPushOnly(), this also tells the
    // UnifiedPush distributor itself we no longer want the endpoint, since (unlike a
    // mere account switch) the user has explicitly chosen a mode without instant push.
    private fun disableInstantPush(account: SingleSignOnAccount?) {
        if (account != null) {
            unregisterWebPushOnly(account)
        }
        UnifiedPush.unregister(this, instance = "default")
    }

    private fun onNotificationModeChanged(mode: NotificationMode) {
        notificationMode = mode
        PushPreference.setMode(this, mode)
        when (mode) {
            NotificationMode.OFF -> {
                DocumentPollWorker.cancel(this)
                disableInstantPush(account)
            }
            NotificationMode.BACKGROUND_ONLY -> {
                DocumentPollWorker.enqueue(this)
                disableInstantPush(account)
            }
            NotificationMode.INSTANT -> {
                DocumentPollWorker.enqueue(this)
                account?.let { syncPushRegistration(it) }
            }
        }
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

// Matches the Ubuntu Touch app's AvatarButton: the account's Nextcloud avatar image
// if it loaded, otherwise a colored circle with the account's first initial. Tapping
// it opens the account picker to switch accounts, same as the UT app's own top bar.
@Composable
private fun AccountAvatarButton(bitmap: Bitmap?, initial: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.switch_account_content_description),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// Offers instant switching between accounts already approved on this device (no
// Files-app UI involved), plus an explicit action to approve a genuinely new one -
// see switchToKnownAccount()/pickAccount() for why these are different flows.
@Composable
private fun AccountSwitcherDialog(
    knownAccountNames: List<String>,
    currentAccountName: String,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.account_switcher_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                knownAccountNames.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAccount(name) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        if (name == currentAccountName) {
                            Text(stringResource(R.string.account_switcher_current), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_switcher_add_account))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close_button))
                }
            }
        }
    }
}

@Composable
private fun SortMode.label(): String = when (this) {
    SortMode.DATE_DESC -> stringResource(R.string.sort_newest_first)
    SortMode.DATE_ASC -> stringResource(R.string.sort_oldest_first)
    SortMode.NAME_ASC -> stringResource(R.string.sort_name_asc)
}

@Composable
private fun formatValidationSummary(summary: ValidationSummary): String {
    val lines = mutableListOf(summary.statusText.ifEmpty { stringResource(R.string.status_signed) })
    summary.signers.forEach { signer ->
        lines.add("")
        lines.add(signer.displayName.ifEmpty { stringResource(R.string.document_unknown_signer) })
        if (signer.signatureLabel.isNotEmpty()) lines.add(signer.signatureLabel)
        if (signer.certificateLabel.isNotEmpty()) lines.add(signer.certificateLabel)
    }
    return lines.joinToString("\n")
}

@Composable
private fun SignInScreen(
    errorMessage: String,
    showInstallNextcloudButton: Boolean,
    onSignIn: () -> Unit,
    onInstallNextcloud: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSignIn) {
            Text(stringResource(R.string.sign_in_button))
        }
        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
        if (showInstallNextcloudButton) {
            OutlinedButton(onClick = onInstallNextcloud) {
                Text(stringResource(R.string.install_nextcloud_button))
            }
        }
    }
}

// Tries the Play Store app first (market:// - opens directly in the Play Store app
// with an "Install" button front and center), falls back to the plain https:// listing
// page if nothing can handle that (e.g. no Play Store on this device at all - the
// same class of gap that caused the crash this whole feature exists to avoid).
private fun openPlayStoreListing(context: android.content.Context, packageName: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: ActivityNotFoundException) {
            // Nothing on this device can open either - the caller's own message
            // already tells the user what to do, nothing more we can offer here.
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
    avatarBitmap: Bitmap?,
    onSwitchAccount: () -> Unit,
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
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_content_description))
                    }
                },
                actions = {
                    SortMenuButton(sortMode = sortMode, onSortModeSelected = onSortModeSelected)
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_content_description))
                    }
                    AccountAvatarButton(
                        bitmap = avatarBitmap,
                        initial = account.userId.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        onClick = onSwitchAccount
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.signed_in_as, account.name),
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
