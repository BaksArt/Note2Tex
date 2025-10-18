package com.baksart.note2tex.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baksart.note2tex.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onSignIn: (login: String, pass: String) -> Unit,
    onGoSignUp: () -> Unit,
    onGoReset: () -> Unit,
    loadingState: StateFlow<UiState>,
    onMessageConsumed: () -> Unit
) {
    val state by loadingState.collectAsState()
    val snack = remember { SnackbarHostState() }
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let { msg -> snack.showSnackbar(msg); onMessageConsumed() }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Вход") }) },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(p),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = login, onValueChange = { login = it },
                    label = { Text("Email или имя пользователя") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSignIn(login.trim(), pass) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.loading) "Входим…" else "Войти") }

                TextButton(onClick = onGoReset, enabled = !state.loading) { Text("Забыли пароль?") }
                OutlinedButton(
                    onClick = onGoSignUp, enabled = !state.loading, modifier = Modifier.fillMaxWidth()
                ) { Text("Создать аккаунт") }
            }
        }
    }
}