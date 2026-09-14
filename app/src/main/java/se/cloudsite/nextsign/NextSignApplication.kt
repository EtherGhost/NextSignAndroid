package se.cloudsite.nextsign

import android.app.Application
import android.content.Context
import se.cloudsite.nextsign.util.LocaleHelper

class NextSignApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }
}
