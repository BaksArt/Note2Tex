package com.baksart.note2tex.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baksart.note2tex.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(
    accessToken: String,
    email: String,
    loadingState: StateFlow<UiState>,
    onAccept: (String) -> Unit,
    onMessageConsumed: () -> Unit
) {
    val state by loadingState.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(accessToken) {
        if (accessToken.isNotBlank()) onAccept(accessToken)
    }
    LaunchedEffect(state.message) {
        state.message?.let { msg -> snack.showSnackbar(msg); onMessageConsumed() }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Подтверждение email") }) },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
            if (accessToken.isBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Мы отправили письмо на ${if (email.isBlank()) "ваш email" else email}.")
                    Text("Перейдите по ссылке из письма, чтобы завершить регистрацию.")
                    if (state.loading) CircularProgressIndicator()
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
