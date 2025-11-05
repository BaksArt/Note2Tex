package com.baksart.note2tex.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baksart.note2tex.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow
import com.baksart.note2tex.R
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

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { ctx.getString(R.string.verify_title) }) },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(p),
            contentAlignment = Alignment.Center
        ) {
            if (accessToken.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ctx.getString(R.string.verify_wait_text, email),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = ctx.getString(R.string.verify_use_link),
                        textAlign = TextAlign.Center
                    )
                    if (state.loading) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
