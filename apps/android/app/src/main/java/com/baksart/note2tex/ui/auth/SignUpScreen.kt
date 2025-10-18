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
fun SignUpScreen(
    onRegistered: (email: String, username: String, pass: String) -> Unit,
    onGoSignIn: () -> Unit,
    loadingState: StateFlow<UiState>,
    onMessageConsumed: () -> Unit
) {
    val state by loadingState.collectAsState()
    val snack = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passRepeat by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { msg -> snack.showSnackbar(msg); onMessageConsumed() }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Регистрация") }) },
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
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passRepeat, onValueChange = { passRepeat = it },
                    label = { Text("Повторите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                if (!localError.isNullOrBlank()) {
                    Text(localError!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        localError = when {
                            email.isBlank() || !email.contains('@') -> "Введите корректный email"
                            username.isBlank() -> "Введите имя пользователя"
                            pass.length < 6 -> "Пароль должен быть не короче 6 символов"
                            pass != passRepeat -> "Пароли не совпадают"
                            else -> null
                        }
                        if (localError == null) {
                            onRegistered(email.trim(), username.trim(), pass)
                        }
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.loading) "Создаём…" else "Создать аккаунт") }

                OutlinedButton(
                    onClick = onGoSignIn,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Уже есть аккаунт — Войти") }
            }
        }
    }
}
