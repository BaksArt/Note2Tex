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
import com.baksart.note2tex.R
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

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(ctx.getString(R.string.signup_title)) }) },
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
                    label = { Text(ctx.getString(R.string.signup_email_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text(ctx.getString(R.string.signup_username_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(ctx.getString(R.string.signup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passRepeat, onValueChange = { passRepeat = it },
                    label = { Text(ctx.getString(R.string.signup_password_repeat_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                if (!localError.isNullOrBlank()) {
                    Text(localError!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        localError = when {
                            email.isBlank() || !email.contains('@') -> ctx.getString(R.string.signup_error_email)
                            username.isBlank() -> ctx.getString(R.string.signup_error_username)
                            pass.length < 6 -> ctx.getString(R.string.signup_error_password)
                            pass != passRepeat -> ctx.getString(R.string.signup_error_password_mismatch)
                            else -> null
                        }
                        if (localError == null) {
                            onRegistered(email.trim(), username.trim(), pass)
                        }
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.loading) ctx.getString(R.string.signup_creating) else ctx.getString(R.string.signup_button)) }

                OutlinedButton(
                    onClick = onGoSignIn,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ctx.getString(R.string.signup_have_account)) }
            }
        }
    }
}
