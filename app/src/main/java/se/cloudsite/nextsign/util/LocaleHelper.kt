package se.cloudsite.nextsign.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

// Manual locale override, not AppCompatDelegate.setApplicationLocales() - this app has
// no appcompat dependency (Compose-only, ComponentActivity), and pulling one in just for
// per-app language would also mean relying on its pre-API-33 "auto storage" backport,
// whose persistence story is tied to Play Feature Delivery/split installs rather than a
// plain SharedPreferences flag. This is the classic wrap-the-Context approach instead:
// works identically on every supported API level (26-36), no new dependency.
object LocaleHelper {
    // Applied in both NextSignApplication.attachBaseContext() (covers process start, so
    // background components like DocumentPollWorker/PushServiceImpl - which only have
    // applicationContext, never an Activity - also get the right language) and
    // MainActivity.attachBaseContext() (covers the Activity recreated by
    // setLanguage()'s recreate() call).
    fun wrap(context: Context): Context {
        val tag = LanguagePreference.get(context)
        val locale = if (tag != null) Locale.forLanguageTag(tag) else systemLocale()
        return applyLocale(context, locale)
    }

    private fun applyLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        // updateConfiguration() is deprecated in favor of createConfigurationContext(),
        // but it's still what actually makes applicationContext.getString() (used
        // outside any Activity, e.g. by DocumentPollWorker) reflect a language changed
        // mid-session without killing the process - createConfigurationContext() alone
        // only affects the new wrapped Context it returns, not the original one every
        // existing applicationContext reference still points at.
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return context.createConfigurationContext(config)
    }

    // The OS's real system locale, unaffected by our own updateConfiguration() calls
    // above - Resources.getSystem() is a separate, framework-wide instance, not this
    // app's own (possibly already-overridden) Resources. Used for "System default".
    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]
}
