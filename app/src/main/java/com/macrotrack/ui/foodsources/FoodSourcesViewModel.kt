package com.macrotrack.ui.foodsources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.domain.model.FoodSource
import com.macrotrack.domain.usecase.foodsource.DeleteFoodSourceUseCase
import com.macrotrack.domain.usecase.foodsource.DownloadFoodSourceUseCase
import com.macrotrack.domain.usecase.foodsource.GetCatalogUseCase
import com.macrotrack.domain.usecase.foodsource.GetInstalledFoodSourcesUseCase
import com.macrotrack.domain.usecase.foodsource.UpdateFoodSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoodSourcesViewModel @Inject constructor(
    private val getInstalledFoodSourcesUseCase: GetInstalledFoodSourcesUseCase,
    private val getCatalogUseCase: GetCatalogUseCase,
    private val downloadFoodSourceUseCase: DownloadFoodSourceUseCase,
    private val updateFoodSourceUseCase: UpdateFoodSourceUseCase,
    private val deleteFoodSourceUseCase: DeleteFoodSourceUseCase,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<FoodSource>>(emptyList())
    private val _isLoadingCatalog = MutableStateFlow(false)
    private val _catalogError = MutableStateFlow<String?>(null)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val _downloadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FoodSourcesUiState> = combine(
        _sources,
        _isLoadingCatalog,
        _catalogError,
        _downloadProgress,
        _downloadError,
    ) { sources, loading, error, progress, dlError ->
        FoodSourcesUiState(
            sources = sources,
            isLoadingCatalog = loading,
            catalogError = error,
            downloadProgress = progress,
            downloadError = dlError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoodSourcesUiState(),
    )

    init {
        viewModelScope.launch {
            getInstalledFoodSourcesUseCase().collect { installed ->
                val current = _sources.value.toMutableList()
                for (inst in installed) {
                    val idx = current.indexOfFirst { it.id == inst.id }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(itemCount = inst.itemCount)
                    }
                }
                _sources.value = current
            }
        }
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _isLoadingCatalog.value = true
            _catalogError.value = null
            getCatalogUseCase().fold(
                onSuccess = { catalogSources ->
                    _sources.value = catalogSources
                    _isLoadingCatalog.value = false
                },
                onFailure = { e ->
                    _catalogError.value = e.message ?: "Failed to load catalog"
                    _isLoadingCatalog.value = false
                },
            )
        }
    }

    fun downloadSource(source: FoodSource) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (source.id to 0f)
            downloadFoodSourceUseCase(source) { progress ->
                _downloadProgress.value = _downloadProgress.value + (source.id to progress)
            }.fold(
                onSuccess = {
                    _downloadProgress.value = _downloadProgress.value - source.id
                    refreshCatalog()
                },
                onFailure = { e ->
                    _downloadProgress.value = _downloadProgress.value - source.id
                    _downloadError.value = e.message ?: "Download failed"
                },
            )
        }
    }

    fun updateSource(source: FoodSource) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (source.id to 0f)
            updateFoodSourceUseCase(source) { progress ->
                _downloadProgress.value = _downloadProgress.value + (source.id to progress)
            }.fold(
                onSuccess = {
                    _downloadProgress.value = _downloadProgress.value - source.id
                    refreshCatalog()
                },
                onFailure = { e ->
                    _downloadProgress.value = _downloadProgress.value - source.id
                    _downloadError.value = e.message ?: "Update failed"
                },
            )
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            deleteFoodSourceUseCase(sourceId)
            refreshCatalog()
        }
    }

    fun clearError() {
        _downloadError.value = null
        _catalogError.value = null
    }
}
