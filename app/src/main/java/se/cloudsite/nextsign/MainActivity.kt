package se.cloudsite.nextsign

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.PdfFieldPlacement
import se.cloudsite.nextsign.model.SignatureElement
import se.cloudsite.nextsign.model.SignerCandidate
import se.cloudsite.nextsign.model.ValidationSummary
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.push.DocumentPollWorker
import se.cloudsite.nextsign.push.PendingSignatureBadges
import se.cloudsite.nextsign.repository.DocumentDownloader
import se.cloudsite.nextsign.repository.DownloadResult
import se.cloudsite.nextsign.repository.LibreSignRepository
import se.cloudsite.nextsign.repository.LoadDocumentsResult
import se.cloudsite.nextsign.repository.SaveSignatureElementResult
import se.cloudsite.nextsign.repository.SearchSignersResult
import se.cloudsite.nextsign.repository.SignResult
import se.cloudsite.nextsign.repository.DeleteDocumentResult
import se.cloudsite.nextsign.repository.SubmitPreparedDocumentResult
import se.cloudsite.nextsign.repository.SignatureElementsResult
import se.cloudsite.nextsign.repository.ValidateResult
import se.cloudsite.nextsign.ui.about.AboutScreen
import se.cloudsite.nextsign.ui.preparedocument.PrepareDocumentGuideScreen
import se.cloudsite.nextsign.ui.preparedocument.PrepareDocumentScreen
import se.cloudsite.nextsign.util.PdfPreviewRenderer
import se.cloudsite.nextsign.ui.account.AccountScreen
import se.cloudsite.nextsign.ui.common.AccountAvatar
import se.cloudsite.nextsign.ui.documentdetail.DeleteDocumentConfirmDialog
import se.cloudsite.nextsign.ui.preparedocument.RemoveFieldConfirmDialog
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
import se.cloudsite.nextsign.util.LanguagePreference
import se.cloudsite.nextsign.util.LocaleHelper
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.PushPreference
import se.cloudsite.nextsign.util.SeenDocumentsStore
import se.cloudsite.nextsign.util.SignatureImageEncoder
import se.cloudsite.nextsign.util.SnoozeDuration
import se.cloudsite.nextsign.util.SnoozeDurationPreference
import se.cloudsite.nextsign.util.ThemeMode
import se.cloudsite.nextsign.util.ThemePreference

private enum class Screen { DOCUMENT_LIST, SIGNATURE_SETUP, SIGNATURE_DRAW, SETTINGS, ABOUT, ACCOUNT, PREPARE_DOCUMENT, PREPARE_DOCUMENT_GUIDE }

// Uses AccountImporter.pickNewAccount()/onActivityResult(), not the newer
// ImportSsoAccount ActivityResultContract shown in the library's current README -
// that class isn't in the released 1.3.4 artifact this app depends on (confirmed by
// inspecting the actual AAR), only on the library's unreleased master branch. This is
// the same pattern the real Nextcloud Notes/Deck apps ship with today.
class MainActivity : ComponentActivity() {

    companion object {
        // Default signature field size in PDF points - matches the size used in the
        // earlier live API spike. The size slider scales both dimensions from this
        // base together (see PrepareDocumentScreen), not independently.
        private const val FIELD_BASE_WIDTH = 150f
        private const val FIELD_BASE_HEIGHT = 50f
    }

    // Applied here too, not just in NextSignApplication - an Activity's own base
    // Context is a fresh wrap of the application Context, not guaranteed to inherit an
    // already-overridden Configuration, and this is also what picks up a language
    // change on the recreate() setLanguage() triggers.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

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
    // Step 1 of document preparation: a PDF shared in from the Nextcloud app. Just
    // held and shown here for now - no API calls yet, see the feasibility doc.
    private var preparedDocumentUri: Uri? by mutableStateOf(null)
    private var preparedDocumentName: String by mutableStateOf("")
    // null = not yet chosen (only asked when more than one account is known - see
    // handleShareIntent). The share intent itself carries no account information.
    private var preparedDocumentAccountName: String? by mutableStateOf(null)
    private var preparedDocumentPreviewBitmap: Bitmap? by mutableStateOf(null)
    private var preparedDocumentPreviewError: Boolean by mutableStateOf(false)
    private var preparedDocumentPreviewLoading: Boolean by mutableStateOf(false)
    // 0-indexed, matches PdfFieldPlacement.page/PdfPreviewRenderer. pageCount
    // starts at 1 so the page indicator has something sane to show before the
    // first render finishes.
    private var preparedDocumentPreviewPage: Int by mutableStateOf(0)
    private var preparedDocumentPageCount: Int by mutableStateOf(1)
    private var signerSearchQuery: String by mutableStateOf("")
    private var signerSearchResults: List<SignerCandidate> by mutableStateOf(emptyList())
    private var signerSearchLoading: Boolean by mutableStateOf(false)
    private var signerSearchErrorMessage: String? by mutableStateOf(null)
    private var selectedSigners: List<SignerCandidate> by mutableStateOf(emptyList())
    // A flat list now, not one entry per signer identify - a signer can need
    // more than one field (e.g. initials on several pages plus a signature on
    // the last one), confirmed as the actual real-world requirement after the
    // earlier one-field-per-signer design kept producing "the box just moves
    // instead of adding a new one" reports that turned out to be the model
    // itself being wrong, not a bug in it.
    private var placedFields: List<PdfFieldPlacement> by mutableStateOf(emptyList())
    private var nextFieldId: Int = 1
    // Who gets a brand new field when tapping empty space on the page - set by
    // tapping a signer row (see PrepareDocumentScreen). Deliberately NOT
    // cleared on page navigation - staying armed across pages is the point
    // now, e.g. arm someone once and tap every page to place their initials
    // on each one.
    private var armedSignerIdentify: String? by mutableStateOf(null)
    // Which specific field the resize slider and long-press-to-remove act on -
    // set by tapping an existing marker, distinct from armedSignerIdentify
    // since one signer can now have several fields.
    private var selectedFieldId: String? by mutableStateOf(null)
    // Field pending a remove confirmation (long-press on its marker) - removes
    // just that one field, keeps the signer themselves in selectedSigners and
    // any other fields they have elsewhere.
    private var pendingFieldRemovalId: String? by mutableStateOf(null)
    private var isSubmittingDocument: Boolean by mutableStateOf(false)
    private var submitSuccessMessage: String? by mutableStateOf(null)
    private var submitErrorMessage: String? by mutableStateOf(null)
    private var pendingDeleteDocument: LibreSignDocument? by mutableStateOf(null)
    private var deleteErrorMessage: String? by mutableStateOf(null)
    private var validationErrorMessage: String? by mutableStateOf(null)
    private var downloadErrorMessage: String? by mutableStateOf(null)

    private var currentScreen: Screen by mutableStateOf(Screen.DOCUMENT_LIST)
    private var showSignOutConfirm: Boolean by mutableStateOf(false)
    // Per-account avatars for the account list screen, keyed by account name - separate
    // from avatarBitmap (which only ever holds the CURRENT account's avatar, for the
    // top-bar button). Populated lazily via loadAccountAvatars() when that screen opens.
    private var accountAvatars: Map<String, Bitmap?> by mutableStateOf(emptyMap())
    private var sortMode: SortMode by mutableStateOf(SortMode.NEEDS_SIGNATURE_FIRST)
    private var showOnlyNeedsAttention: Boolean by mutableStateOf(false)
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
    // The CURRENT account's own mode - kept in sync with accountNotificationModes
    // below, used for logic that only ever cares about "the account open right now"
    // (e.g. whether to eagerly sync push registration on load).
    private var notificationMode: NotificationMode by mutableStateOf(NotificationMode.BACKGROUND_ONLY)
    // Every known account's own mode, for the Settings screen's per-account list -
    // same lazy-refresh pattern as accountAvatars above.
    private var accountNotificationModes: Map<String, NotificationMode> by mutableStateOf(emptyMap())
    // Every known account's own badge-snooze duration, same pattern.
    private var accountSnoozeDurations: Map<String, SnoozeDuration> by mutableStateOf(emptyMap())
    private var languageTag: String? by mutableStateOf(null)

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handlePickedImage(uri)
        }
    }

    // The "Open file manager" guide button (see PrepareDocumentGuideScreen) - a
    // plain launch-the-files-app Intent (CATEGORY_APP_FILES) has no way to get a
    // picked file back to this activity at all, so tapping a PDF there just
    // returned to NextSign with nothing happening (confirmed live). GetContent
    // is a real picker with a result callback, so it goes straight into the
    // prepare-document flow - no separate manual "now share it" step needed.
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            beginPreparingDocument(uri)
        }
    }

    // Only needed for the push-notification test to show a system notification on
    // Android 13+ (POST_NOTIFICATIONS) - the rest of the app doesn't post any.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result observed via ContextCompat when actually posting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        themeMode = ThemePreference.get(this)
        languageTag = LanguagePreference.get(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

        // Must run after the AccountHistory.remember() call above - a fresh
        // upgrade's only known account needs to already be in that list to inherit
        // the old global value.
        PushPreference.migrateFromLegacyGlobalMode(this, AccountHistory.list(this))
        notificationMode = account?.let { PushPreference.getMode(this, it.name) } ?: NotificationMode.BACKGROUND_ONLY
        refreshAccountNotificationModes()
        refreshAccountSnoozeDurations()

        // Tier 1 of the push notifications plan - a periodic-sync fallback that works
        // regardless of whether real-time push (Tier 2, below) is available. Runs per
        // known account, each gated by its own notification mode - see
        // DocumentPollWorker. Always enqueued (idempotent via KEEP): whether any
        // account actually needs polling is decided inside doWork(), not here.
        DocumentPollWorker.enqueue(this)

        handleShareIntent(intent)

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
                                                    openAccountScreen()
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
                                                label = { Text(stringResource(R.string.drawer_prepare_document)) },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    currentScreen = Screen.PREPARE_DOCUMENT_GUIDE
                                                }
                                            )
                                            NavigationDrawerItem(
                                                label = { Text(stringResource(R.string.drawer_settings)) },
                                                selected = false,
                                                onClick = {
                                                    drawerScope.launch { drawerState.close() }
                                                    refreshAccountNotificationModes()
                                                    refreshAccountSnoozeDurations()
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
                                    val filteredDocuments = if (showOnlyNeedsAttention) documents.filter { it.canSignNow } else documents
                                    AppScreen(
                                        account = currentAccount,
                                        documents = sortDocuments(filteredDocuments, sortMode),
                                        // Distinct from "no documents at all" - the filter hid
                                        // everything, not an actually-empty list.
                                        filterHidAllDocuments = showOnlyNeedsAttention && documents.isNotEmpty() && filteredDocuments.isEmpty(),
                                        loading = loading,
                                        errorMessage = errorMessage,
                                        selectedDocument = selectedDocument,
                                        signing = signingUuid == selectedDocument?.uuid,
                                        validating = validatingUuid == selectedDocument?.uuid,
                                        downloading = downloadingUuid == selectedDocument?.uuid,
                                        sortMode = sortMode,
                                        onSortModeSelected = { sortMode = it },
                                        showOnlyNeedsAttention = showOnlyNeedsAttention,
                                        onToggleNeedsAttentionFilter = { showOnlyNeedsAttention = !showOnlyNeedsAttention },
                                        avatarBitmap = avatarBitmap,
                                        onSwitchAccount = { openAccountScreen() },
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
                                        },
                                        onDeleteDocument = { document -> pendingDeleteDocument = document }
                                    )
                                }

                                pendingDeleteDocument?.let { document ->
                                    DeleteDocumentConfirmDialog(
                                        documentName = document.name,
                                        onConfirm = {
                                            pendingDeleteDocument = null
                                            deleteDocument(currentAccount, document)
                                        },
                                        onDismiss = { pendingDeleteDocument = null }
                                    )
                                }

                                deleteErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = stringResource(R.string.document_delete_error_dialog_title),
                                        message = message,
                                        onDismiss = { deleteErrorMessage = null }
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

                                submitSuccessMessage?.let { name ->
                                    MessageDialog(
                                        title = stringResource(R.string.prepare_document_submit_success_title),
                                        message = stringResource(R.string.prepare_document_submit_success_message, name),
                                        onDismiss = { submitSuccessMessage = null }
                                    )
                                }

                                submitErrorMessage?.let { message ->
                                    MessageDialog(
                                        title = stringResource(R.string.prepare_document_submit_error_title),
                                        message = message,
                                        onDismiss = { submitErrorMessage = null }
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
                                languageTag = languageTag,
                                onLanguageSelected = { tag -> setLanguage(tag) },
                                knownAccountNames = AccountHistory.list(this@MainActivity),
                                currentAccountName = currentAccount.name,
                                notificationModesByAccount = accountNotificationModes,
                                onNotificationModeSelected = { accountName, mode ->
                                    onAccountNotificationModeChanged(accountName, mode)
                                },
                                snoozeDurationsByAccount = accountSnoozeDurations,
                                onSnoozeDurationSelected = { accountName, duration ->
                                    SnoozeDurationPreference.set(this@MainActivity, accountName, duration)
                                    accountSnoozeDurations = accountSnoozeDurations + (accountName to duration)
                                },
                                // Live device state, not app state - re-checked each time
                                // Settings is shown rather than cached, so installing ntfy
                                // and coming back immediately reflects it with no extra
                                // signal needed.
                                hasPushDistributor = UnifiedPush.getDistributors(this@MainActivity).isNotEmpty(),
                                onInstallPushHelper = { openPlayStoreListing(this@MainActivity, "io.heckel.ntfy") },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )

                            Screen.ABOUT -> AboutScreen(onBack = { currentScreen = Screen.DOCUMENT_LIST })

                            Screen.PREPARE_DOCUMENT_GUIDE -> PrepareDocumentGuideScreen(
                                nextcloudInstalled = packageManager.getLaunchIntentForPackage("com.nextcloud.client") != null,
                                onOpenNextcloud = {
                                    packageManager.getLaunchIntentForPackage("com.nextcloud.client")?.let { startActivity(it) }
                                },
                                onInstallNextcloud = { openPlayStoreListing(this@MainActivity, "com.nextcloud.client") },
                                onOpenFileManager = { openFileManager() },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )

                            Screen.PREPARE_DOCUMENT -> {
                                val chosenAccountName = preparedDocumentAccountName
                                // Debounced search - the identify-account/search endpoint
                                // does a real server round trip per keystroke otherwise.
                                // Resolved non-destructively (AccountImporter, not
                                // SingleAccountHelper) - picking an account for this one
                                // document must not disturb the app's own active account.
                                LaunchedEffect(signerSearchQuery, chosenAccountName) {
                                    if (chosenAccountName == null || signerSearchQuery.isBlank()) {
                                        signerSearchResults = emptyList()
                                        signerSearchErrorMessage = null
                                        signerSearchLoading = false
                                        return@LaunchedEffect
                                    }
                                    signerSearchLoading = true
                                    signerSearchErrorMessage = null
                                    delay(400)
                                    val resolvedAccount = try {
                                        AccountImporter.getSingleSignOnAccount(this@MainActivity, chosenAccountName)
                                    } catch (e: NextcloudFilesAppAccountNotFoundException) {
                                        null
                                    }
                                    if (resolvedAccount != null) {
                                        // A real search error (network outage, server error) must
                                        // not look identical to "no matches" - the former collapsed
                                        // to emptyList() silently before, with no way to tell them apart.
                                        when (val result = withContext(Dispatchers.IO) { repository.searchSigners(resolvedAccount, signerSearchQuery) }) {
                                            is SearchSignersResult.Success -> {
                                                signerSearchResults = result.candidates
                                                signerSearchErrorMessage = null
                                            }
                                            is SearchSignersResult.Failure -> {
                                                signerSearchResults = emptyList()
                                                signerSearchErrorMessage = result.message
                                            }
                                        }
                                    } else {
                                        signerSearchResults = emptyList()
                                        signerSearchErrorMessage = getString(R.string.account_switch_failed)
                                    }
                                    signerSearchLoading = false
                                }
                                val effectiveArmedIdentify = armedSignerIdentify
                                    ?.takeIf { id -> selectedSigners.any { it.identify == id } }
                                val selectedField = selectedFieldId?.let { id -> placedFields.firstOrNull { it.id == id } }

                                PrepareDocumentScreen(
                                    documentName = preparedDocumentName,
                                    knownAccountNames = AccountHistory.list(this@MainActivity),
                                    selectedAccountName = preparedDocumentAccountName,
                                    previewBitmap = preparedDocumentPreviewBitmap,
                                    previewLoading = preparedDocumentPreviewLoading,
                                    previewError = preparedDocumentPreviewError,
                                    previewPage = preparedDocumentPreviewPage,
                                    previewPageCount = preparedDocumentPageCount,
                                    onPreviousPage = {
                                        val uri = preparedDocumentUri
                                        if (uri != null && preparedDocumentPreviewPage > 0) {
                                            preparedDocumentPreviewPage -= 1
                                            loadPreviewPage(uri, preparedDocumentPreviewPage)
                                        }
                                    },
                                    onNextPage = {
                                        val uri = preparedDocumentUri
                                        if (uri != null && preparedDocumentPreviewPage < preparedDocumentPageCount - 1) {
                                            preparedDocumentPreviewPage += 1
                                            loadPreviewPage(uri, preparedDocumentPreviewPage)
                                        }
                                    },
                                    signerSearchQuery = signerSearchQuery,
                                    onSignerSearchQueryChange = { signerSearchQuery = it },
                                    signerSearchResults = signerSearchResults,
                                    signerSearchLoading = signerSearchLoading,
                                    signerSearchErrorMessage = signerSearchErrorMessage,
                                    selectedSigners = selectedSigners,
                                    placedFields = placedFields,
                                    armedSignerIdentify = effectiveArmedIdentify,
                                    onArmSigner = { identify -> armedSignerIdentify = identify },
                                    fieldSizeFactor = selectedField?.let { it.width / FIELD_BASE_WIDTH } ?: 1f,
                                    hasSelectedFieldOnCurrentPage = selectedField?.page == preparedDocumentPreviewPage,
                                    onFieldSizeFactorChange = { factor ->
                                        val old = selectedField
                                        if (old != null) {
                                            val centerX = old.left + old.width / 2
                                            val centerY = old.top + old.height / 2
                                            val newWidth = FIELD_BASE_WIDTH * factor
                                            val newHeight = FIELD_BASE_HEIGHT * factor
                                            val bitmap = preparedDocumentPreviewBitmap
                                            val maxLeft = if (bitmap != null) (bitmap.width - newWidth).coerceAtLeast(0f) else centerX
                                            val maxTop = if (bitmap != null) (bitmap.height - newHeight).coerceAtLeast(0f) else centerY
                                            val newLeft = (centerX - newWidth / 2).coerceIn(0f, maxLeft)
                                            val newTop = (centerY - newHeight / 2).coerceIn(0f, maxTop)
                                            placedFields = placedFields.map {
                                                if (it.id == old.id) it.copy(left = newLeft, top = newTop, width = newWidth, height = newHeight) else it
                                            }
                                        }
                                    },
                                    onAddSigner = { candidate ->
                                        if (selectedSigners.none { it.identify == candidate.identify }) {
                                            selectedSigners = selectedSigners + candidate
                                        }
                                        armedSignerIdentify = candidate.identify
                                        signerSearchQuery = ""
                                        signerSearchResults = emptyList()
                                        signerSearchErrorMessage = null
                                    },
                                    onRemoveSigner = { candidate ->
                                        selectedSigners = selectedSigners.filterNot { it.identify == candidate.identify }
                                        placedFields = placedFields.filterNot { it.identify == candidate.identify }
                                        if (armedSignerIdentify == candidate.identify) armedSignerIdentify = null
                                    },
                                    onTapPlaceField = { xPt, yPt ->
                                        // Always adds a brand new field - never moves/overwrites
                                        // an existing one (see onDragField for that). Tagged with
                                        // whichever page is currently on screen, so arming someone
                                        // once and tapping each page in turn places one field per
                                        // page for them - the actual real-world requirement, found
                                        // out the hard way after the earlier "one field per signer,
                                        // tapping just moves it" design kept producing live bug
                                        // reports that turned out to be the model itself being wrong.
                                        val targetIdentify = effectiveArmedIdentify
                                        if (targetIdentify != null) {
                                            val bitmap = preparedDocumentPreviewBitmap
                                            val fieldWidth = FIELD_BASE_WIDTH
                                            val fieldHeight = FIELD_BASE_HEIGHT
                                            val maxLeft = if (bitmap != null) (bitmap.width - fieldWidth).coerceAtLeast(0f) else xPt
                                            val maxTop = if (bitmap != null) (bitmap.height - fieldHeight).coerceAtLeast(0f) else yPt
                                            val left = (xPt - fieldWidth / 2).coerceIn(0f, maxLeft)
                                            val top = (yPt - fieldHeight / 2).coerceIn(0f, maxTop)
                                            val newId = "field_${nextFieldId++}"
                                            placedFields = placedFields + PdfFieldPlacement(
                                                id = newId,
                                                identify = targetIdentify,
                                                left = left,
                                                top = top,
                                                width = fieldWidth,
                                                height = fieldHeight,
                                                page = preparedDocumentPreviewPage
                                            )
                                            selectedFieldId = newId
                                        }
                                    },
                                    onFieldTap = { fieldId ->
                                        val field = placedFields.firstOrNull { it.id == fieldId }
                                        if (field != null) {
                                            armedSignerIdentify = field.identify
                                            selectedFieldId = fieldId
                                        }
                                    },
                                    onDragField = { fieldId, dxPt, dyPt ->
                                        val old = placedFields.firstOrNull { it.id == fieldId }
                                        if (old != null) {
                                            val bitmap = preparedDocumentPreviewBitmap
                                            val maxLeft = if (bitmap != null) (bitmap.width - old.width).coerceAtLeast(0f) else Float.MAX_VALUE
                                            val maxTop = if (bitmap != null) (bitmap.height - old.height).coerceAtLeast(0f) else Float.MAX_VALUE
                                            val newLeft = (old.left + dxPt).coerceIn(0f, maxLeft)
                                            val newTop = (old.top + dyPt).coerceIn(0f, maxTop)
                                            placedFields = placedFields.map {
                                                if (it.id == fieldId) it.copy(left = newLeft, top = newTop) else it
                                            }
                                        }
                                    },
                                    onFieldLongPress = { fieldId -> pendingFieldRemovalId = fieldId },
                                    isSubmitting = isSubmittingDocument,
                                    onSubmit = { submitPreparedDocument() },
                                    onSelectAccount = { name -> preparedDocumentAccountName = name },
                                    onBack = {
                                        preparedDocumentUri = null
                                        preparedDocumentName = ""
                                        preparedDocumentAccountName = null
                                        preparedDocumentPreviewBitmap = null
                                        preparedDocumentPreviewError = false
                                        preparedDocumentPreviewPage = 0
                                        preparedDocumentPageCount = 1
                                        signerSearchQuery = ""
                                        signerSearchResults = emptyList()
                                        signerSearchErrorMessage = null
                                        selectedSigners = emptyList()
                                        placedFields = emptyList()
                                        nextFieldId = 1
                                        armedSignerIdentify = null
                                        selectedFieldId = null
                                        currentScreen = Screen.DOCUMENT_LIST
                                    }
                                )

                                pendingFieldRemovalId?.let { fieldId ->
                                    val field = placedFields.firstOrNull { it.id == fieldId }
                                    val signerName = field?.let { f -> selectedSigners.firstOrNull { it.identify == f.identify }?.displayName }.orEmpty()
                                    RemoveFieldConfirmDialog(
                                        signerName = signerName,
                                        onConfirm = {
                                            placedFields = placedFields.filterNot { it.id == fieldId }
                                            if (selectedFieldId == fieldId) selectedFieldId = null
                                            // Arms the signer whose field was just removed -
                                            // without this, whoever was armed before (if
                                            // anyone) stays armed and the just-cleared signer
                                            // is never offered for placement again until the
                                            // user explicitly taps their row, which read as
                                            // "the send button stays disabled forever" since
                                            // nothing made it obvious how to place their field
                                            // again.
                                            if (field != null) armedSignerIdentify = field.identify
                                            pendingFieldRemovalId = null
                                        },
                                        onDismiss = { pendingFieldRemovalId = null }
                                    )
                                }
                            }

                            Screen.ACCOUNT -> AccountScreen(
                                knownAccountNames = AccountHistory.list(this@MainActivity),
                                currentAccountName = currentAccount.name,
                                avatarsByName = accountAvatars,
                                onSelectAccount = { name ->
                                    currentScreen = Screen.DOCUMENT_LIST
                                    if (name != currentAccount.name) {
                                        switchToKnownAccount(name)
                                    }
                                },
                                onAddAccount = {
                                    currentScreen = Screen.DOCUMENT_LIST
                                    pickAccount()
                                },
                                onSignOut = {
                                    currentScreen = Screen.DOCUMENT_LIST
                                    showSignOutConfirm = true
                                },
                                onBack = { currentScreen = Screen.DOCUMENT_LIST }
                            )
                        }

                        if (showSignOutConfirm) {
                            val nextAccountName = AccountHistory.list(this@MainActivity)
                                .firstOrNull { it != currentAccount.name }
                            SignOutConfirmDialog(
                                nextAccountName = nextAccountName,
                                onConfirm = {
                                    showSignOutConfirm = false
                                    signOut()
                                },
                                onDismiss = { showSignOutConfirm = false }
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
        // Each account has its own UnifiedPush instance now, so leaving an account
        // open doesn't unregister anything - it keeps receiving Instant push
        // independently in the background. Only an explicit mode change (see
        // onNotificationModeChanged) or removing the account entirely (see signOut())
        // unregisters it.
        ApiProvider.invalidate(newAccount)
        documents = emptyList()
        signatureElementsByType = emptyMap()
        signaturePreviewBitmap = null
        avatarBitmap = null
        selectedDocumentUuid = null
        errorMessage = ""
        account = newAccount
        // Each account has its own notification mode - reflect the newly-current
        // account's own stored value, not whatever the previous account had.
        notificationMode = PushPreference.getMode(this, newAccount.name)
    }

    // Signs out of the CURRENT account only - other approved accounts are unaffected
    // and stay usable, matching the user's explicit choice between "sign out of
    // everything" and "sign out of just the current one, keep using the others" (a real
    // multi-account "sign out of everything" isn't achievable anyway: the SSO library
    // has no public API to revoke a specific account's grant, only to clear which one
    // NextSign treats as current). Forgets the account from AccountHistory so it
    // genuinely requires the full re-approval flow again, rather than staying one tap
    // away via instant switching - that's what makes this a real sign-out rather than
    // just a switch. If another known account remains, switches straight into it
    // (switchToKnownAccount() already handles clearing per-account state and
    // unregistering push for the account being left) instead of dropping to a bare
    // sign-in screen - signing out of one account shouldn't end the session for
    // accounts still in use. Only when no other account remains does this clear the SSO
    // library's own persisted "current account" pointer directly
    // (commitCurrentAccount(context, null) - confirmed via the library's actual
    // bytecode that storing null is equivalent to removing the key) - clearing only
    // this Activity's local `account` field would look like a sign-out in this session
    // but silently undo itself on the next app launch, since onCreate() re-reads the
    // same persisted account, and DocumentPollWorker/PushServiceImpl read it
    // independently of this Activity too.
    private fun signOut() {
        val current = account ?: return
        AccountHistory.forget(this, current.name)
        // The account being forgotten needs its own push torn down fully (not just a
        // server-side unsubscribe) - unlike a plain switch, it won't be around to keep
        // using its UnifiedPush instance afterward.
        if (PushPreference.getMode(this, current.name) == NotificationMode.INSTANT) {
            disableInstantPush(current)
        }
        val nextAccountName = AccountHistory.list(this).firstOrNull()
        if (nextAccountName != null) {
            switchToKnownAccount(nextAccountName)
            return
        }
        SingleAccountHelper.commitCurrentAccount(this, null)
        documents = emptyList()
        signatureElementsByType = emptyMap()
        signaturePreviewBitmap = null
        avatarBitmap = null
        selectedDocumentUuid = null
        errorMessage = ""
        account = null
        notificationMode = NotificationMode.BACKGROUND_ONLY
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
                // The system account picker AccountImporter.pickNewAccount() opens has
                // no API to exclude accounts already known to this app (confirmed from
                // the library's own source - it always passes null for the exclusion
                // list) - it lists every Nextcloud-type account on the device, already
                // added here or not. Re-picking one already in AccountHistory is
                // handled as a plain instant switch (same as tapping it in the
                // switcher's own list) rather than repeating the full "just imported a
                // new account" bookkeeping below, which is only meaningful the first
                // time an account is added.
                if (ssoAccount.name in AccountHistory.list(this)) {
                    switchToKnownAccount(ssoAccount.name)
                    return@onActivityResult
                }
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
                // Each account has its own UnifiedPush instance - the previous
                // account isn't touched by switching, see switchToKnownAccount().
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
                notificationMode = PushPreference.getMode(this, ssoAccount.name)
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
        if (handleShareIntent(intent)) return
        account?.let { refresh(it) }
    }

    // Step 1 of document preparation - a PDF shared in from the Nextcloud app lands
    // here (ACTION_SEND, see the manifest's intent-filter). Only reads the file's
    // display name via the content resolver for now; the file's own bytes aren't
    // touched until a later step actually submits it. Returns true if this intent was
    // actually a share (so callers can skip their normal handling).
    private fun handleShareIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND) return false
        // Some senders put the file on ClipData instead of (or in addition to) the
        // plain extra - fall back to it rather than assuming EXTRA_STREAM is always set.
        val uri = run {
            val fromExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            fromExtra ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        }
        if (uri == null) return false
        beginPreparingDocument(uri)
        return true
    }

    // Shared by handleShareIntent (a PDF shared in from another app) and
    // filePickerLauncher (a PDF picked directly via the system file picker,
    // see openFileManager) - both just want "start the prepare-document flow
    // for this file".
    private fun beginPreparingDocument(uri: Uri) {
        preparedDocumentUri = uri
        preparedDocumentName = queryDisplayName(uri)
        // Neither a share nor a picked file carries account info - default
        // straight through when only one account is known, otherwise make the
        // user pick (see PrepareDocumentScreen).
        val knownAccounts = AccountHistory.list(this)
        preparedDocumentAccountName = if (knownAccounts.size <= 1) {
            knownAccounts.firstOrNull() ?: account?.name
        } else {
            null
        }
        currentScreen = Screen.PREPARE_DOCUMENT
        preparedDocumentPreviewPage = 0
        preparedDocumentPageCount = 1
        loadPreviewPage(uri, 0)
    }

    // Shared by the initial share-receive and the prev/next page buttons in
    // PrepareDocumentScreen - both just want "render this page of this document
    // and update the preview state when it lands".
    private fun loadPreviewPage(uri: Uri, pageIndex: Int) {
        preparedDocumentPreviewBitmap = null
        preparedDocumentPreviewError = false
        preparedDocumentPreviewLoading = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { PdfPreviewRenderer.renderPage(this@MainActivity, uri, pageIndex) }
            // Guards against a share received (or a page turned) while an older
            // render was still in flight landing after the newer one and silently
            // swapping in the wrong page - same pattern as refresh()'s account
            // guard elsewhere in this file.
            if (uri != preparedDocumentUri || pageIndex != preparedDocumentPreviewPage) return@launch
            preparedDocumentPreviewBitmap = result?.bitmap
            preparedDocumentPreviewError = result == null
            preparedDocumentPreviewLoading = false
            if (result != null) {
                preparedDocumentPageCount = result.pageCount
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: ""
            }
        }
        return ""
    }

    // Originally tried CATEGORY_APP_FILES (launch "the" file manager app without
    // guessing an OEM-specific package name) with a plain ACTION_GET_CONTENT
    // fallback fired via startActivity(). Both are dead ends for this button's
    // actual purpose: CATEGORY_APP_FILES just opens an app, with no way to get a
    // picked file back to NextSign at all, and a fire-and-forget
    // ACTION_GET_CONTENT has nowhere for its result to land either - tapping a
    // PDF just returned to NextSign with nothing happening (confirmed live).
    // filePickerLauncher (GetContent, registered above with a real result
    // callback) fixes both: a real system picker, scoped to PDFs, that feeds
    // the chosen file straight into the prepare-document flow - no separate
    // manual "now share it" step needed either.
    private fun openFileManager() {
        filePickerLauncher.launch("application/pdf")
    }

    private fun submitPreparedDocument() {
        val uri = preparedDocumentUri ?: return
        val accountName = preparedDocumentAccountName ?: return
        val fileName = preparedDocumentName.ifEmpty { getString(R.string.document_untitled) }
        val signers = selectedSigners
        val fields = placedFields
        isSubmittingDocument = true
        lifecycleScope.launch {
            val resolvedAccount = try {
                AccountImporter.getSingleSignOnAccount(this@MainActivity, accountName)
            } catch (e: NextcloudFilesAppAccountNotFoundException) {
                null
            }
            if (resolvedAccount == null) {
                isSubmittingDocument = false
                submitErrorMessage = getString(R.string.account_switch_failed)
                return@launch
            }
            val base64 = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { input -> Base64.encodeToString(input.readBytes(), Base64.NO_WRAP) }
            }
            if (base64 == null) {
                isSubmittingDocument = false
                submitErrorMessage = getString(R.string.downloaded_file_empty)
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                repository.submitPreparedDocument(resolvedAccount, fileName, base64, signers, fields)
            }
            isSubmittingDocument = false
            when (result) {
                is SubmitPreparedDocumentResult.Success -> {
                    submitSuccessMessage = fileName
                    preparedDocumentUri = null
                    preparedDocumentName = ""
                    preparedDocumentAccountName = null
                    preparedDocumentPreviewBitmap = null
                    preparedDocumentPreviewError = false
                    preparedDocumentPreviewPage = 0
                    preparedDocumentPageCount = 1
                    signerSearchQuery = ""
                    signerSearchResults = emptyList()
                    signerSearchErrorMessage = null
                    selectedSigners = emptyList()
                    placedFields = emptyList()
                    nextFieldId = 1
                    armedSignerIdentify = null
                    selectedFieldId = null
                    currentScreen = Screen.DOCUMENT_LIST
                    // Only refresh the visible list if the document was prepared under
                    // the app's currently active account - otherwise this would yank
                    // the user's foreground view over to a different account's data.
                    if (accountName == account?.name) {
                        account?.let { refresh(it) }
                    }
                }
                is SubmitPreparedDocumentResult.Failure -> {
                    submitErrorMessage = result.message
                }
            }
        }
    }

    private fun deleteDocument(account: SingleSignOnAccount, document: LibreSignDocument) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteDocument(account, document.fileId) }
            when (result) {
                is DeleteDocumentResult.Success -> refresh(account)
                is DeleteDocumentResult.Failure -> deleteErrorMessage = result.message
            }
        }
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
                    SeenDocumentsStore.markSeen(applicationContext, account.name, result.documents.map { it.uuid }.toSet())
                    PendingSignatureBadges.sync(applicationContext, account.name, result.documents)
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

    private fun openAccountScreen() {
        currentScreen = Screen.ACCOUNT
        loadAccountAvatars(AccountHistory.list(this))
    }

    // Populates accountAvatars for every known account so the account list can show a
    // real avatar per row, not just for whichever one happens to be current.
    // AccountImporter.getSingleSignOnAccount() resolves a SingleSignOnAccount for any
    // already-approved account by name without touching which one is "current" (unlike
    // SingleAccountHelper.commitCurrentAccount(), which would actually switch).
    private fun loadAccountAvatars(names: List<String>) {
        names.forEach { name ->
            if (name in accountAvatars) return@forEach
            lifecycleScope.launch {
                val resolved = try {
                    AccountImporter.getSingleSignOnAccount(this@MainActivity, name)
                } catch (e: NextcloudFilesAppAccountNotFoundException) {
                    null
                }
                val bitmap = resolved?.let {
                    when (val result = withContext(Dispatchers.IO) { documentDownloader.downloadAvatar(it) }) {
                        is DownloadResult.Success -> BitmapFactory.decodeFile(result.file.path)
                        is DownloadResult.Failure -> null
                    }
                }
                accountAvatars = accountAvatars + (name to bitmap)
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
                                UnifiedPush.register(this@MainActivity, instance = account.name, vapid = vapid)
                                android.util.Log.i("NextSignPush", "UnifiedPush.register() call returned for ${account.name}")
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
    // to its own UnifiedPush endpoint, without tearing down the endpoint/instance
    // itself (see disableInstantPush(), which does that part).
    private fun unregisterWebPushOnly(account: SingleSignOnAccount) {
        lifecycleScope.launch {
            try {
                val api = withContext(Dispatchers.IO) { ApiProvider.getNotificationsApi(applicationContext, account) }
                val response = withContext(Dispatchers.IO) { api.unregisterWebPush().execute() }
                android.util.Log.i("NextSignPush", "unregisterWebPush for ${account.name}: HTTP ${response.code()}")
            } catch (e: Exception) {
                android.util.Log.e("NextSignPush", "unregisterWebPush failed", e)
            }
        }
    }

    // Full teardown of this account's Tier 2 registration - unlike
    // unregisterWebPushOnly(), this also tells the UnifiedPush distributor we no
    // longer want this account's own instance/endpoint. Used when the user explicitly
    // turns this account's mode away from Instant, or forgets the account entirely
    // (see onNotificationModeChanged()/signOut()) - a mere account switch does
    // neither, since each account's instance keeps working independently in the
    // background regardless of which account is currently open.
    private fun disableInstantPush(account: SingleSignOnAccount) {
        unregisterWebPushOnly(account)
        UnifiedPush.unregister(this, instance = account.name)
    }

    private fun refreshAccountNotificationModes() {
        accountNotificationModes = AccountHistory.list(this).associateWith { name -> PushPreference.getMode(this, name) }
    }

    private fun refreshAccountSnoozeDurations() {
        accountSnoozeDurations = AccountHistory.list(this).associateWith { name -> SnoozeDurationPreference.get(this, name) }
    }

    // Settings screen callback - changes one specific account's mode, whether or not
    // it's the one currently open. DocumentPollWorker's periodic work itself is never
    // stopped here (see enqueue() in onCreate): it loops every known account and
    // already skips ones set to Off, so cancelling it on Off would also stop polling
    // for any other account that still wants it.
    private fun onAccountNotificationModeChanged(accountName: String, mode: NotificationMode) {
        PushPreference.setMode(this, accountName, mode)
        accountNotificationModes = accountNotificationModes + (accountName to mode)
        if (accountName == account?.name) {
            notificationMode = mode
        }
        // AccountImporter.getSingleSignOnAccount() (not
        // SingleAccountHelper.getCurrentSingleSignOnAccount()) - this may not be the
        // currently-open account at all.
        val targetAccount = try {
            AccountImporter.getSingleSignOnAccount(this, accountName)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } ?: return
        when (mode) {
            NotificationMode.OFF, NotificationMode.BACKGROUND_ONLY -> disableInstantPush(targetAccount)
            NotificationMode.INSTANT -> syncPushRegistration(targetAccount)
        }
    }

    // recreate() rather than just updating languageTag - Compose's stringResource()
    // reads resources through the Activity's own Configuration, which only reflects a
    // new locale once attachBaseContext() runs again on a fresh Activity instance,
    // not by recomposing the existing one.
    private fun setLanguage(tag: String?) {
        LanguagePreference.set(this, tag)
        recreate()
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
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort_button_content_description, sortMode.label())
            )
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
    AccountAvatar(
        bitmap = bitmap,
        initial = initial,
        size = 32.dp,
        modifier = Modifier
            .padding(end = 12.dp)
            .clickable(onClick = onClick),
        contentDescription = stringResource(R.string.switch_account_content_description)
    )
}

@Composable
private fun SignOutConfirmDialog(nextAccountName: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_confirm_title)) },
        text = {
            Text(
                if (nextAccountName != null) {
                    stringResource(R.string.sign_out_switch_confirm_message, nextAccountName)
                } else {
                    stringResource(R.string.sign_out_confirm_message)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.account_switcher_sign_out)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun SortMode.label(): String = when (this) {
    SortMode.NEEDS_SIGNATURE_FIRST -> stringResource(R.string.sort_needs_signature_first)
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
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSignIn) {
            Text(stringResource(R.string.sign_in_button))
        }
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
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
    filterHidAllDocuments: Boolean,
    loading: Boolean,
    errorMessage: String,
    selectedDocument: LibreSignDocument?,
    signing: Boolean,
    validating: Boolean,
    downloading: Boolean,
    sortMode: SortMode,
    onSortModeSelected: (SortMode) -> Unit,
    showOnlyNeedsAttention: Boolean,
    onToggleNeedsAttentionFilter: () -> Unit,
    avatarBitmap: Bitmap?,
    onSwitchAccount: () -> Unit,
    onMenuClick: () -> Unit,
    onRefresh: () -> Unit,
    onDocumentClick: (LibreSignDocument) -> Unit,
    onDismissDetail: () -> Unit,
    onSignClick: (LibreSignDocument) -> Unit,
    onValidateClick: (LibreSignDocument) -> Unit,
    onOpenFileClick: (LibreSignDocument) -> Unit,
    onDeleteDocument: (LibreSignDocument) -> Unit
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
                    IconButton(onClick = onToggleNeedsAttentionFilter) {
                        Icon(
                            if (showOnlyNeedsAttention) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(
                                if (showOnlyNeedsAttention) R.string.filter_needs_attention_on_content_description
                                else R.string.filter_needs_attention_off_content_description
                            )
                        )
                    }
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
                emptyMessage = stringResource(
                    if (filterHidAllDocuments) R.string.document_list_empty_filtered else R.string.document_list_empty
                ),
                currentAccountUserId = account.userId,
                onRefresh = onRefresh,
                onDocumentClick = onDocumentClick,
                onDeleteDocument = onDeleteDocument
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
