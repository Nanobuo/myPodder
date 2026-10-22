package com.nanobuo.mypodder.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Riceve l'esito dell'installazione dell'aggiornamento dall'installer di sistema. */
@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Il sistema chiede conferma: mostra la sua schermata di installazione.
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // l'app viene riavviata con la nuova versione
            else -> updates.installFailed()
        }
    }
}
