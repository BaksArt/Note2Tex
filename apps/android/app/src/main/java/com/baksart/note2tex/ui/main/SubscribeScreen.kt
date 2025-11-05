package com.baksart.note2tex.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baksart.note2tex.R
import com.baksart.note2tex.domain.model.AccountMe
import com.baksart.note2tex.presentation.viewmodel.PremiumViewModel
import com.baksart.note2tex.presentation.viewmodel.SettingsViewModel
import com.baksart.note2tex.presentation.viewmodel.SubPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    onBack: () -> Unit,
    settingsVm: SettingsViewModel = viewModel(),
    premiumVm: PremiumViewModel = viewModel()
) {
    val me by settingsVm.state.collectAsState()
    val ui by premiumVm.state.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); premiumVm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.sub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.sub_features_title), style = MaterialTheme.typography.titleMedium)
            Feature(text = stringResource(R.string.sub_feat_unlimited_month))
            Feature(text = stringResource(R.string.sub_feat_unlimited_history))
            Feature(text = stringResource(R.string.sub_feat_unlock_premium))

            Divider()

            Text(stringResource(R.string.sub_choose_period), style = MaterialTheme.typography.titleMedium)
            PeriodOption(
                title = stringResource(R.string.sub_period_month),
                subtitle = stringResource(R.string.sub_period_month_desc),
                selected = ui.selected == SubPeriod.MONTH
            ) { premiumVm.select(SubPeriod.MONTH) }
            PeriodOption(
                title = stringResource(R.string.sub_period_quarter),
                subtitle = stringResource(R.string.sub_period_quarter_desc),
                selected = ui.selected == SubPeriod.QUARTER
            ) { premiumVm.select(SubPeriod.QUARTER) }
            PeriodOption(
                title = stringResource(R.string.sub_period_year),
                subtitle = stringResource(R.string.sub_period_year_desc),
                selected = ui.selected == SubPeriod.YEAR
            ) { premiumVm.select(SubPeriod.YEAR) }

            Spacer(Modifier.height(8.dp))

            // Кнопки
            val isPremiumNow = me.me?.plan?.equals("premium", ignoreCase = true) == true

            Button(
                onClick = {
                    premiumVm.grant {
                        settingsVm.refresh()
                    }
                },
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sub_pay)) }

            OutlinedButton(
                onClick = {
                    premiumVm.revoke {
                        settingsVm.refresh()
                    }
                },
                enabled = !ui.loading && isPremiumNow,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sub_cancel)) }




        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("•")
        Text(text)
    }
}

@Composable
private fun PeriodOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
