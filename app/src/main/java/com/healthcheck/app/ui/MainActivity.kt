package com.healthcheck.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthTheme {
                App()
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (!vm.configured) {
        NotConfiguredScreen()
        return
    }
    when (state.auth) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AuthState.LoggedOut -> LoginScreen(vm, state)
        AuthState.LoggedIn -> {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        vm = vm,
                        state = state,
                        onEditMedication = { owner, medId -> nav.navigate("edit/$owner/${medId ?: "new"}") },
                        onOpenPerson = { id -> nav.navigate("person/$id") },
                    )
                }
                composable("person/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    PersonScreen(
                        vm = vm,
                        personId = id,
                        state = state,
                        onBack = { nav.popBackStack() },
                        onEditMedication = { medId -> nav.navigate("edit/$id/${medId ?: "new"}") },
                    )
                }
                composable("edit/{owner}/{med}") { entry ->
                    val owner = entry.arguments?.getString("owner") ?: return@composable
                    val med = entry.arguments?.getString("med")?.takeIf { it != "new" }
                    MedicationEditScreen(
                        vm = vm,
                        state = state,
                        ownerId = owner,
                        medicationId = med,
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
