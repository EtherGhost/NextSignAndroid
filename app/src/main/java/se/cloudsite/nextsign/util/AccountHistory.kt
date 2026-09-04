package se.cloudsite.nextsign.util

import android.content.Context

// Local record of Nextcloud account names (SingleSignOnAccount.name, e.g.
// "tobbe@cloudsite.se") this device has already imported/approved via the Files
// app's SSO flow. Lets the account switcher offer instant switching between known
// accounts (SingleAccountHelper.commitCurrentAccount() + getCurrentSingleSignOnAccount(),
// no UI involved) instead of always going through AccountImporter.pickNewAccount()'s
// full re-approval flow, which is only actually needed for a genuinely new account.
object AccountHistory {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_KNOWN_ACCOUNTS = "known_account_names"

    fun list(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_KNOWN_ACCOUNTS, emptySet()).orEmpty().sorted()
    }

    fun remember(context: Context, accountName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_KNOWN_ACCOUNTS, emptySet()).orEmpty()
        if (accountName in current) return
        prefs.edit().putStringSet(KEY_KNOWN_ACCOUNTS, current + accountName).apply()
    }

    // Removes an account from the switcher's known-accounts list - used by sign-out, so
    // that account genuinely requires the full re-approval flow again next time, rather
    // than staying one tap away via instant switching. Does not touch the account's own
    // SSO grant in Android's account manager (there's no public API in the SSO library
    // for that), so other apps using the same account (e.g. the Files app) are
    // unaffected, and re-adding it later via "Add another account" is still fast.
    fun forget(context: Context, accountName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_KNOWN_ACCOUNTS, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_KNOWN_ACCOUNTS, current - accountName).apply()
    }
}
