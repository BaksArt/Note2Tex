package com.baksart.note2tex.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baksart.note2tex.R
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.domain.model.AppLanguage
import com.baksart.note2tex.domain.model.AppTheme
import com.baksart.note2tex.presentation.viewmodel.SettingsUiState
import com.baksart.note2tex.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onChangePassword: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenSubscription: () -> Unit
) {
    val state by vm.state.collectAsState()
    val theme by vm.theme.collectAsState()
    val defaultExport by vm.defaultExport.collectAsState()
    val lang by vm.language.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier.height(64.dp)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = p.calculateTopPadding(),
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            item {
                AccountHeader(
                    state = state,
                    onRefresh = { vm.refresh() },
                    onOpenSubscription = onOpenSubscription
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onChangePassword,
                            enabled = state.me != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.change_password)) }

                        OutlinedButton(
                            onClick = { vm.logout { onLoggedOut() } },
                            enabled = true,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.logout)) }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.app_settings),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK).forEach { option ->
                                val label = when (option) {
                                    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                                    AppTheme.LIGHT  -> stringResource(R.string.theme_light)
                                    AppTheme.DARK   -> stringResource(R.string.theme_dark)
                                }
                                FilterChip(
                                    selected = theme == option,
                                    onClick = { vm.setTheme(option) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Divider()

                        Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(AppLanguage.SYSTEM, AppLanguage.RU, AppLanguage.EN).forEach { option ->
                                val label = when (option) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                    AppLanguage.RU     -> stringResource(R.string.language_ru)
                                    AppLanguage.EN     -> stringResource(R.string.language_en)
                                }
                                FilterChip(
                                    selected = lang == option,
                                    onClick = { vm.setLanguage(option) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Divider()

                        Text(
                            stringResource(R.string.format),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val options = listOf(ExportFormat.PDF, ExportFormat.LATEX, ExportFormat.DOCX)
                            @Composable
                            fun labelOf(f: ExportFormat) = when (f) {
                                ExportFormat.PDF   -> stringResource(R.string.fmt_pdf)
                                ExportFormat.LATEX -> stringResource(R.string.fmt_tex)
                                ExportFormat.DOCX  -> stringResource(R.string.fmt_docx)
                            }
                            options.forEach { fmt ->
                                FilterChip(
                                    selected = defaultExport == fmt,
                                    onClick = { vm.setDefaultExport(fmt) },
                                    label = { Text(labelOf(fmt)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =================== ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ =================== */

@Composable
private fun AccountHeader(
    state: SettingsUiState,
    onRefresh: () -> Unit,
    onOpenSubscription: () -> Unit
) {
    val me = state.me
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.account),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
                }
            }

            if (me == null) {
                Text(if (state.loading) stringResource(R.string.loading) else stringResource(R.string.failed_to_load))
                return@ElevatedCard
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(me.username, style = MaterialTheme.typography.titleLarge)
                Text(
                    me.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            val planPretty = when (me.plan.lowercase()) {
                "premium" -> stringResource(R.string.plan_premium)
                "free"    -> stringResource(R.string.plan_free)
                else      -> me.plan
            }
            AssistChip(
                onClick = onOpenSubscription,
                label = { Text(stringResource(R.string.plan_label, planPretty)) }
            )

            val monthLimit = 10
            val totalLimit = 10
            QuotaRow(
                title = stringResource(R.string.quota_month),
                used = me.monthProjects.coerceIn(0, monthLimit),
                limit = monthLimit
            )
            QuotaRow(
                title = stringResource(R.string.quota_total),
                used = me.totalProjects.coerceIn(0, totalLimit),
                limit = totalLimit
            )
        }
    }
}

@Composable
private fun QuotaRow(title: String, used: Int, limit: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text("$used / $limit", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { if (limit <= 0) 0f else used.toFloat() / limit.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
        )
    }
}
