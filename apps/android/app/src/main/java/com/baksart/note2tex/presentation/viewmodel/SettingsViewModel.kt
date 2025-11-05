package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.R
import com.baksart.note2tex.data.repo.AccountRepository
import com.baksart.note2tex.data.repo.AccountResult
import com.baksart.note2tex.data.repo.AuthRepository
import com.baksart.note2tex.data.storage.AppSettingsStore
import com.baksart.note2tex.di.ServiceLocator
import com.baksart.note2tex.domain.model.AccountMe
import com.baksart.note2tex.domain.model.AppTheme
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val me: AccountMe? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val accountRepo: AccountRepository = ServiceLocator.accountRepository(app)
    private val authRepo: AuthRepository = ServiceLocator.authRepository(app)
    private val settingsStore = AppSettingsStore(app)

    private val appCtx: Application get() = getApplication()

    val theme: StateFlow<AppTheme> =
        settingsStore.themeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)
    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsStore.setTheme(theme) }

    val defaultExport = settingsStore.defaultExportFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExportFormat.PDF)

    fun setDefaultExport(format: ExportFormat) = viewModelScope.launch {
        settingsStore.setDefaultExport(format)
    }

    val language = settingsStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.SYSTEM)
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch {
        settingsStore.setLanguage(lang)
    }

    private val _state = MutableStateFlow(SettingsUiState(loading = true))
    val state: StateFlow<SettingsUiState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        when (val r = accountRepo.me()) {
            is AccountResult.Ok  -> _state.value = SettingsUiState(loading = false, me = r.data)
            is AccountResult.Err -> _state.value = SettingsUiState(loading = false, message = r.message ?: appCtx.getString(R.string.error_unknown))
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun logout(onDone: () -> Unit) = viewModelScope.launch {
        authRepo.logout()
        onDone()
    }
}
