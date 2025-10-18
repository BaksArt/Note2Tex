package com.baksart.note2tex.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baksart.note2tex.presentation.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    onSend: (email: String) -> Unit,
    loadingState: StateFlow<UiState>,
    onMessageConsumed: () -> Unit
) {
    val state by loadingState.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var cooldown by remember { mutableStateOf(0) }

    suspend fun startCooldown() {
        cooldown = 30
        while (cooldown > 0) {
            delay(1_000)
            cooldown -= 1
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            snack.showSnackbar(msg)
            onMessageConsumed()
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Сброс пароля") }) },
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
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val buttonEnabled = !state.loading && email.isNotBlank() && cooldown == 0
                val buttonText = when {
                    state.loading -> "Отправляем…"
                    cooldown > 0  -> "Отправить снова через ${cooldown}s"
                    else          -> if (state.message == null) "Отправить" else "Отправить снова"
                }

                Button(
                    onClick = {
                        val e = email.trim()
                        if (e.isNotEmpty()) {
                            onSend(e)
                            scope.launch { startCooldown() }
                        }
                    },
                    enabled = buttonEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}
