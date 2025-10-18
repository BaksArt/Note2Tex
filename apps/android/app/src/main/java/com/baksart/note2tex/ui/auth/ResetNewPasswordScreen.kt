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
fun ResetNewPasswordScreen(
    token: String,
    onSubmit: (newPassword: String) -> Unit,
    loadingState: StateFlow<UiState>,
    onMessageConsumed: () -> Unit
) {
    val state by loadingState.collectAsState()
    val snack = remember { SnackbarHostState() }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { msg -> snack.showSnackbar(msg); onMessageConsumed() }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Новый пароль") }) },
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
                    value = p1, onValueChange = { p1 = it },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = p2, onValueChange = { p2 = it },
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
                            p1.length < 6 -> "Пароль должен быть не короче 6 символов"
                            p1 != p2 -> "Пароли не совпадают"
                            else -> null
                        }
                        if (localError == null) onSubmit(p1)
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.loading) "Сохраняем…" else "Сохранить") }
            }
        }
    }
}
