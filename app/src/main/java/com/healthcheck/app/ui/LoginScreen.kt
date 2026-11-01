package com.healthcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(vm: AppViewModel, state: UiState) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, state, snackbar)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Filled.Medication, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("HealthCheck", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Lembretes de remédios para você e para quem você cuida",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { vm.signInWithGoogle() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Spacer(Modifier.width(12.dp))
                Text("Continuar com Google")
            }
            Text("ou use e-mail e senha", style = MaterialTheme.typography.bodySmall)
            if (register) {
                OutlinedTextField(name, { name = it }, label = { Text("Seu nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Senha") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (register) vm.signUp(name, email, password) else vm.signIn(email, password) },
                enabled = !state.busy && email.isNotBlank() && password.length >= 6 && (!register || name.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (register) "Criar conta" else "Entrar")
            }
            TextButton(onClick = { register = !register }) {
                Text(if (register) "Já tenho conta — entrar" else "Não tem conta? Cadastre-se")
            }
        }
    }
}

@Composable
fun NotConfiguredScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Backend não configurado", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Defina SUPABASE_URL e SUPABASE_ANON_KEY no arquivo local.properties " +
                "(ou nos secrets do GitHub Actions) e gere o APK novamente. Veja o README.",
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MessageEffect(vm: AppViewModel, state: UiState, snackbar: SnackbarHostState) {
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        vm.consumeMessage()
        snackbar.showSnackbar(msg)
    }
}
