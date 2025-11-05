package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.R
import com.baksart.note2tex.data.repo.PremiumRepository
import com.baksart.note2tex.data.repo.PremiumResult
import com.baksart.note2tex.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SubPeriod { MONTH, QUARTER, YEAR }

data class SubscribeUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val selected: SubPeriod = SubPeriod.MONTH
)

class PremiumViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: PremiumRepository = ServiceLocator.premiumRepository(app)
    private val ctx = app.applicationContext

    private val _state = MutableStateFlow(SubscribeUiState())
    val state: StateFlow<SubscribeUiState> = _state

    fun select(p: SubPeriod) { _state.value = _state.value.copy(selected = p) }

    fun grant(onOk: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        val period = when (_state.value.selected) {
            SubPeriod.MONTH -> "1m"
            SubPeriod.QUARTER -> "3m"
            SubPeriod.YEAR -> "12m"
        }
        when (val r = repo.grant(period)) {
            is PremiumResult.Ok -> {
                _state.value = SubscribeUiState(message = ctx.getString(R.string.sub_granted))
                onOk()
            }
            is PremiumResult.Err -> _state.value =
                _state.value.copy(loading = false, message = r.message ?: ctx.getString(R.string.error_unknown))
        }
    }


    fun revoke(onOk: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        when (val r = repo.revoke()) {
            is PremiumResult.Ok  -> { _state.value = SubscribeUiState(message = ctx.getString(R.string.sub_revoked)); onOk() }
            is PremiumResult.Err -> _state.value = _state.value.copy(loading = false, message = r.message ?: ctx.getString(R.string.error_unknown))
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}
