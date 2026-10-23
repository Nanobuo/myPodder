package com.nanobuo.mypodder.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.discover.Countries

/** Elenco dei paesi con ricerca; la prima voce torna al paese automatico del dispositivo. */
@Composable
fun CountryPickerDialog(
    selected: String,
    automatic: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = remember { Countries.all() }
    var filter by remember { mutableStateOf("") }
    val shown = remember(filter) { all.filter { it.name.contains(filter, ignoreCase = true) || it.code.equals(filter, true) } }
    val default = remember { Countries.of(Countries.default()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_country)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (filter.isBlank()) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.country_automatic, default.flag + " " + default.name)) },
                                trailingContent = if (automatic) {
                                    { Icon(Icons.Rounded.Check, null) }
                                } else {
                                    null
                                },
                                modifier = Modifier.clickable { onSelect("") },
                            )
                        }
                    }
                    items(shown, key = { it.code }) { country ->
                        ListItem(
                            headlineContent = { Text(country.flag + "  " + country.name) },
                            trailingContent = if (!automatic && country.code == selected) {
                                { Icon(Icons.Rounded.Check, null) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable { onSelect(country.code) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
