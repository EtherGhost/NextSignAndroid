package se.cloudsite.nextsign.util

import android.content.Context

// null means "follow the system language" - the default, and the only state this app
// had before an in-app picker existed. A stored value is a plain BCP-47 tag ("de",
// "nb", ...), not an enum like ThemeMode - the supported-language list is defined once,
// in LanguageScreen's SUPPORTED_LANGUAGES, rather than duplicated here.
object LanguagePreference {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun get(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE_TAG, null)
    }

    fun set(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, tag)
            .apply()
    }
}
