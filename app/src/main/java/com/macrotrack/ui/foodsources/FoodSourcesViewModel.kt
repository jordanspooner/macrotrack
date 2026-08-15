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

    private val _catalogSources = MutableStateFlow<List<FoodSource>?>(null)
    private val _installedSources = MutableStateFlow<List<FoodSource>>(emptyList())
    private val _isLoadingCatalog = MutableStateFlow(false)
    private val _catalogError = MutableStateFlow<String?>(null)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val _downloadError = MutableStateFlow<String?>(null)
    private val _sources: StateFlow<List<FoodSource>> = combine(
        _catalogSources,
        _installedSources,
    ) { catalogSources, installedSources ->
        catalogSources?.let { mergeInstalledSources(it, installedSources) } ?: installedSources
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                _installedSources.value = installed
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
                    _catalogSources.value = catalogSources
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
            _downloadError.value = null
            _downloadProgress.value = _downloadProgress.value + (source.id to 0f)
            downloadFoodSourceUseCase(source) { progress ->
                _downloadProgress.value = _downloadProgress.value + (source.id to progress)
            }.fold(
                onSuccess = {
                    _downloadProgress.value = _downloadProgress.value - source.id
                    _downloadError.value = null
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
            _downloadError.value = null
            _downloadProgress.value = _downloadProgress.value + (source.id to 0f)
            updateFoodSourceUseCase(source) { progress ->
                _downloadProgress.value = _downloadProgress.value + (source.id to progress)
            }.fold(
                onSuccess = {
                    _downloadProgress.value = _downloadProgress.value - source.id
                    _downloadError.value = null
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

    private fun mergeInstalledSources(
        catalogSources: List<FoodSource>,
        installedSources: List<FoodSource>,
    ): List<FoodSource> {
        val installedById = installedSources.associateBy { it.id }
        val merged = catalogSources.map { catalogSource ->
            val installed = installedById[catalogSource.id]
            if (installed == null) {
                catalogSource.copy(
                    itemCount = 0,
                    status = if (catalogSource.isUserSource) {
                        FoodSource.Status.MY_FOODS
                    } else {
                        FoodSource.Status.NOT_INSTALLED
                    },
                )
            } else {
                catalogSource.copy(
                    itemCount = installed.itemCount,
                    installedAt = installed.installedAt,
                    status = if (catalogSource.status == FoodSource.Status.NOT_INSTALLED) {
                        installed.status
                    } else {
                        catalogSource.status
                    },
                )
            }
        }
        val catalogIds = merged.mapTo(mutableSetOf()) { it.id }
        return merged + installedSources.filter { it.id !in catalogIds }
    }
}
