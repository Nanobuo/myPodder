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
import androidx.compose.material.icons.rounded.Password
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
    var showPassword by remember { mutableStateOf(false) }

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
            headlineContent = { Text(stringResource(R.string.sync_set_password)) },
            supportingContent = { Text(stringResource(R.string.sync_set_password_summary)) },
            leadingContent = { Icon(Icons.Rounded.Password, null) },
            modifier = Modifier.clickable { showPassword = true },
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
    if (showPassword && session != null) {
        PasswordDialog(sync, scope) { showPassword = false }
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
    var password by remember { mutableStateOf("") }
    // Dopo l'invio si resta sul passo del codice, anche se la verifica fallisce.
    val sentTo = when (login) {
        is LoginState.CodeSent -> login.email
        is LoginState.Verifying -> login.email
        is LoginState.Error -> login.email
        else -> null
    }
    val busy = login is LoginState.Sending || login is LoginState.Verifying || login is LoginState.SigningIn
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.sync_password_optional)) },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (validEmail) if (password.isNotEmpty()) sync.signInWithPassword(email, password) else sync.sendCode(email)
                        }),
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
                        if (login.wrongPassword) stringResource(R.string.sync_wrong_password)
                        else stringResource(R.string.sync_login_error, login.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (sentTo == null) {
                if (password.isNotEmpty()) {
                    TextButton(onClick = { sync.signInWithPassword(email, password) }, enabled = validEmail && !busy) {
                        Text(stringResource(R.string.sync_verify))
                    }
                } else {
                    TextButton(onClick = { sync.sendCode(email) }, enabled = validEmail && !busy) {
                        Text(stringResource(R.string.sync_send_code))
                    }
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

@Composable
private fun PasswordDialog(sync: SyncManager, scope: CoroutineScope, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = password.length >= 8
    val saved = stringResource(R.string.sync_password_saved)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Password, null) },
        title = { Text(stringResource(R.string.sync_set_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_password_hint))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.sync_password_new)) },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                error?.let {
                    Text(stringResource(R.string.sync_password_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        sync.setPassword(password)
                            .onSuccess {
                                Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            .onFailure { error = it.message.orEmpty() }
                        saving = false
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
