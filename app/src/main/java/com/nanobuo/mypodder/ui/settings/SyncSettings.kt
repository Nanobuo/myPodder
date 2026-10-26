package com.nanobuo.mypodder.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.sync.LoginState
import com.nanobuo.mypodder.data.sync.SyncManager
import com.nanobuo.mypodder.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Voci della sezione "Sincronizzazione": accesso, stato, uscita ed eliminazione dell'account. */
@Composable
fun SyncSettings(sync: SyncManager, scope: CoroutineScope) {
    val info by sync.info.collectAsStateWithLifecycle()
    val status by sync.status.collectAsStateWithLifecycle()
    val login by sync.login.collectAsStateWithLifecycle()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Accesso dal link dell'email mentre la finestra non è aperta: mostra comunque l'esito.
    LaunchedEffect(login) {
        if (login is LoginState.Verifying || login is LoginState.Error) showLogin = true
    }

    val session = info.session
    if (session == null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_sign_in)) },
            supportingContent = { Text(stringResource(R.string.sync_sign_in_summary)) },
            leadingContent = { Icon(Icons.Rounded.CloudSync, null) },
            modifier = Modifier.clickable { showLogin = true },
        )
    } else {
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_account)) },
            supportingContent = { Text(session.email) },
            leadingContent = { Icon(Icons.Rounded.AccountCircle, null) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_now)) },
            supportingContent = {
                Text(
                    when (val st = status) {
                        SyncStatus.Running -> stringResource(R.string.sync_running)
                        is SyncStatus.Error -> stringResource(R.string.sync_error, st.message)
                        SyncStatus.Idle -> if (info.lastSyncAt > 0) {
                            stringResource(
                                R.string.sync_last,
                                DateUtils.getRelativeTimeSpanString(info.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                            )
                        } else {
                            stringResource(R.string.sync_never)
                        }
                    },
                    color = if (status is SyncStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = { Icon(Icons.Rounded.Sync, null) },
            modifier = Modifier.clickable(enabled = status != SyncStatus.Running) { scope.launch { sync.syncNow() } },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_sign_out)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            modifier = Modifier.clickable { confirmSignOut = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_delete_account)) },
            supportingContent = { Text(stringResource(R.string.sync_delete_account_summary)) },
            leadingContent = { Icon(Icons.Rounded.DeleteForever, null) },
            modifier = Modifier.clickable { confirmDelete = true },
        )
    }

    if (showLogin && session == null) {
        LoginDialog(sync, login) {
            showLogin = false
            sync.resetLogin()
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sync_sign_out)) },
            text = { Text(stringResource(R.string.sync_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; sync.signOut() }) { Text(stringResource(R.string.sync_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sync_delete_account)) },
            text = { Text(stringResource(R.string.sync_delete_account_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; scope.launch { sync.deleteAccount() } }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LoginDialog(sync: SyncManager, login: LoginState, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    // Dopo l'invio si resta sul passo del codice, anche se la verifica fallisce.
    val sentTo = when (login) {
        is LoginState.CodeSent -> login.email
        is LoginState.Verifying -> login.email
        is LoginState.Error -> login.email
        else -> null
    }
    val busy = login is LoginState.Sending || login is LoginState.Verifying
    val validEmail = email.contains('@') && email.substringAfter('@').contains('.')

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudSync, null) },
        title = { Text(stringResource(R.string.sync_sign_in)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sentTo == null) {
                    Text(stringResource(R.string.sync_email_hint))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text(stringResource(R.string.sync_email)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (validEmail) sync.sendCode(email) }),
                    )
                } else {
                    Text(stringResource(R.string.sync_code_hint, sentTo))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter(Char::isDigit).take(10) },
                        label = { Text(stringResource(R.string.sync_code)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (code.length >= 6) sync.verifyCode(sentTo, code) }),
                    )
                }
                if (login is LoginState.Error) {
                    Text(
                        stringResource(R.string.sync_login_error, login.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (sentTo == null) {
                TextButton(onClick = { sync.sendCode(email) }, enabled = validEmail && !busy) {
                    Text(stringResource(R.string.sync_send_code))
                }
            } else {
                TextButton(onClick = { sync.verifyCode(sentTo, code) }, enabled = code.length >= 6 && !busy) {
                    Text(stringResource(R.string.sync_verify))
                }
            }
        },
        dismissButton = {
            if (sentTo != null) {
                TextButton(onClick = { code = ""; sync.resetLogin() }, enabled = !busy) { Text(stringResource(R.string.sync_change_email)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
