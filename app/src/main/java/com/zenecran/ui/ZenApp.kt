package com.zenecran.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

@Composable
fun ZenApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.visible = true
        vm.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.visible = false }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (vm.visible) vm.refresh()
        }
    }

    val items = listOf(
        "Accueil" to Icons.Rounded.Home,
        "Limites" to Icons.Rounded.Timer,
        "Horaires" to Icons.Rounded.Schedule,
        "Contenu" to Icons.Rounded.VideoLibrary,
        "Réglages" to Icons.Rounded.Settings,
    )

    Scaffold(
        containerColor = Z.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = Z.card) {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Z.onTeal,
                            indicatorColor = Z.teal,
                            selectedTextColor = Z.teal,
                            unselectedIconColor = Z.textLo,
                            unselectedTextColor = Z.textLo,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        when (tab) {
            0 -> HomeScreen(vm, pad)
            1 -> LimitsScreen(vm, pad)
            2 -> SchedulesScreen(vm, pad)
            3 -> FeaturesScreen(vm, pad)
            else -> SettingsScreen(vm, pad)
        }
    }
}
