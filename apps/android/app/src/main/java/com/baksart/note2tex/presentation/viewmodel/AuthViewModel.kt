package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.data.repo.AuthRepository
import com.baksart.note2tex.data.repo.AuthResult
import com.baksart.note2tex.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(val loading: Boolean = false, val message: String? = null)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AuthRepository = ServiceLocator.authRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private fun setLoading() { _state.value = UiState(loading = true) }
    private fun setError(code: Int?, e: String?, m: String?) {
        _state.value = UiState(message = m ?: e ?: "Ошибка")
    }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun register(email: String, username: String, pass: String, onOk: () -> Unit) = viewModelScope.launch {
        setLoading()
        when (val r = repo.register(email, username, pass)) {
            is AuthResult.Ok -> { _state.value = UiState(message = "Проверьте почту"); onOk() }
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun resendVerification(email: String) = viewModelScope.launch {
        setLoading()
        when (val r = repo.resendVerification(email)) {
            is AuthResult.Ok -> _state.value = UiState(message = "Письмо отправлено")
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun login(login: String, pass: String, onOk: () -> Unit) = viewModelScope.launch {
        setLoading()
        when (val r = repo.login(login, pass)) {
            is AuthResult.Ok -> { _state.value = UiState(); onOk() }
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun forgot(email: String) = viewModelScope.launch {
        setLoading()
        when (val r = repo.forgot(email)) {
            is AuthResult.Ok -> _state.value = UiState(message = "Если email существует — письмо отправлено")
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun reset(token: String, newPassword: String, onOk: () -> Unit) = viewModelScope.launch {
        setLoading()
        when (val r = repo.reset(token, newPassword)) {
            is AuthResult.Ok -> { _state.value = UiState(message = "Пароль обновлён"); onOk() }
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun acceptAccessTokenFromDeepLink(accessToken: String, onOk: () -> Unit) = viewModelScope.launch {
        setLoading()
        when (val r = repo.saveAccessToken(accessToken)) {
            is AuthResult.Ok -> { _state.value = UiState(); onOk() }
            is AuthResult.Err -> setError(r.code, r.error, r.message)
        }
    }

    fun checkAuth(onAuthed: () -> Unit, onUnauthed: () -> Unit) = viewModelScope.launch {
        if (repo.hasToken()) onAuthed() else onUnauthed()
    }
}
