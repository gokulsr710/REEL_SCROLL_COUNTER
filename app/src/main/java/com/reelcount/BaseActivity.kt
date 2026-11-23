package com.reelcount

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/** Applies the Light / Dark / System choice from settings to every screen. */
open class BaseActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        val mode = Prefs.get(base).getString(Prefs.THEME, "system")
        if (mode == "system") return super.attachBaseContext(base)
        val config = Configuration(base.resources.configuration)
        val night = if (mode == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}
