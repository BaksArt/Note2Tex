package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.R
import com.baksart.note2tex.di.ServiceLocator
import com.baksart.note2tex.domain.model.ProjectItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val items: List<ProjectItem> = emptyList()
) {
    val hasProcessing: Boolean
        get() = items.any { it.status.equals("processing", ignoreCase = true) }
}

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.projectsRepository(app)
    private val appCtx: Application get() = getApplication()

    private val _state = MutableStateFlow(ProjectsUiState(loading = true))
    val state: StateFlow<ProjectsUiState> = _state

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            try {
                val list = repo.listProjects()
                _state.value = ProjectsUiState(loading = false, items = list)

                if (_state.value.hasProcessing) startPolling()
            } catch (t: Throwable) {
                _state.value = ProjectsUiState(
                    loading = false,
                    message = t.message ?: appCtx.getString(R.string.failed_to_load)
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                try {
                    val list = repo.listProjects()
                    _state.value = _state.value.copy(items = list, message = null)
                    if (!_state.value.hasProcessing) break
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(
                        message = t.message ?: appCtx.getString(R.string.error_generic)
                    )
                }
            }
        }
    }

    fun delete(id: String) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                repo.deleteProject(id)
                val list = repo.listProjects()
                _state.value = _state.value.copy(
                    loading = false,
                    items = list,
                    message = appCtx.getString(R.string.ok)
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = t.message ?: appCtx.getString(R.string.error_generic)
                )
            }
        }
    }

    fun rename(id: String, title: String?, description: String?) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                repo.updateProject(id, title, description)
                val list = repo.listProjects()
                _state.value = _state.value.copy(
                    loading = false,
                    items = list,
                    message = appCtx.getString(R.string.ok)
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = t.message ?: appCtx.getString(R.string.error_generic)
                )
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
