package se.cloudsite.nextsign.repository

import android.content.Context
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.File
import java.io.FileOutputStream
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.network.LIBRESIGN_API_ENDPOINT

sealed class DownloadResult {
    data class Success(val file: File, val mimeType: String) : DownloadResult()
    data class Failure(val message: String) : DownloadResult()
}

// Downloads a document, or a registered signature/initials preview image, to the app's
// cache. Uses NextcloudAPI.performNetworkRequestV2() directly rather than a Retrofit
// interface for both: neither is a Gson-deserializable OCS response (a PDF or PNG
// isn't JSON), and the SSO bridge's Retrofit adapter always deserializes success
// bodies via Gson regardless of declared type - the same abstract-class problem hit
// while building the step-1 connectivity test, this time with no JsonElement-style
// escape hatch since the response isn't JSON at all.
class DocumentDownloader(private val context: Context) {

    fun downloadDocument(account: SingleSignOnAccount, document: LibreSignDocument): DownloadResult {
        if (document.filePath.isEmpty()) {
            return DownloadResult.Failure(context.getString(R.string.document_location_unknown))
        }
        return downloadToCache(
            account = account,
            path = document.filePath,
            fileName = safeCacheFileName(document.name, ".pdf"),
            subdir = "DocumentDownloads",
            defaultMimeType = "application/pdf"
        )
    }

    fun downloadSignaturePreview(account: SingleSignOnAccount, nodeId: Int): DownloadResult {
        return downloadToCache(
            account = account,
            path = "${LIBRESIGN_API_ENDPOINT}signature/elements/preview/$nodeId",
            fileName = "signature-$nodeId.png",
            subdir = "ImageDownloads",
            defaultMimeType = "image/png"
        )
    }

    // account.userId is the bare Nextcloud login (e.g. "tobbe"), not account.name
    // (the SSO library's own compound "tobbe@cloudsite.se" display identifier) -
    // matches the path the Ubuntu Touch app builds (NextCommon's UrlHelpers.js
    // avatarUrl()), confirmed against the SingleSignOnAccount class's actual fields
    // rather than guessed.
    fun downloadAvatar(account: SingleSignOnAccount): DownloadResult {
        val encodedUserId = java.net.URLEncoder.encode(account.userId, "UTF-8")
        return downloadToCache(
            account = account,
            path = "/index.php/avatar/$encodedUserId/64",
            fileName = "avatar-${account.userId}.png",
            subdir = "AvatarDownloads",
            defaultMimeType = "image/png"
        )
    }

    private fun downloadToCache(
        account: SingleSignOnAccount,
        path: String,
        fileName: String,
        subdir: String,
        defaultMimeType: String
    ): DownloadResult {
        return try {
            val api = ApiProvider.getNextcloudApi(context, account)
            val request = NextcloudRequest.Builder()
                .setMethod("GET")
                .setUrl(path)
                .build()
            val response = api.performNetworkRequestV2(request)

            val cacheDir = File(context.cacheDir, subdir)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                return DownloadResult.Failure(context.getString(R.string.cache_dir_prepare_failed))
            }

            // Timestamp prefix only for uniqueness across repeated downloads of the
            // same file, not for any cryptographic property - unlike the Ubuntu Touch
            // app's SHA1-based naming, this doesn't need to be unguessable.
            val targetFile = File(cacheDir, "${System.currentTimeMillis()}-$fileName")

            response.body.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.length() == 0L) {
                targetFile.delete()
                return DownloadResult.Failure(context.getString(R.string.downloaded_file_empty))
            }

            val contentType = response.getPlainHeader("Content-Type")?.value
                ?.substringBefore(';')
                ?.trim()
                .orEmpty()
            DownloadResult.Success(targetFile, contentType.ifEmpty { defaultMimeType })
        } catch (e: Exception) {
            DownloadResult.Failure(e.message ?: e.toString())
        }
    }

    private fun safeCacheFileName(rawName: String, extension: String): String {
        var name = rawName.trim()
        if (name.isEmpty()) {
            name = "document"
        }
        name = name.replace(Regex("[\\\\/\\r\\n]+"), "-")
        if (!name.endsWith(extension, ignoreCase = true)) {
            name += extension
        }
        return name
    }
}
