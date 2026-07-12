package com.milesxue.pixeldone.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle

internal class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleInstallStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallStatus(intent)
    }

    private fun handleInstallStatus(statusIntent: Intent) {
        if (statusIntent.action != ACTION_INSTALL_STATUS) {
            finish()
            return
        }
        if (
            statusIntent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            ) == PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                statusIntent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            confirmationIntent?.let { runCatching { startActivity(it) } }
        }
        finish()
    }

    internal companion object {
        const val ACTION_INSTALL_STATUS = "com.milesxue.pixeldone.action.UPDATE_INSTALL_STATUS"
    }
}
