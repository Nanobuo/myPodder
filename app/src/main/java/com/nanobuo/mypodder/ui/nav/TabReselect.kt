package com.nanobuo.mypodder.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlin.reflect.KClass

/** Eventi "scheda già selezionata toccata di nuovo", identificati dalla classe della rotta. */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<KClass<*>>> { MutableSharedFlow() }

/** Esegue [action] (di solito tornare in cima alla lista) quando si ritocca la scheda [route]. */
@Composable
fun OnTabReselected(route: KClass<*>, action: suspend () -> Unit) {
    val events = LocalTabReselect.current
    val current by rememberUpdatedState(action)
    LaunchedEffect(events, route) {
        events.filter { it == route }.collect { current() }
    }
}
