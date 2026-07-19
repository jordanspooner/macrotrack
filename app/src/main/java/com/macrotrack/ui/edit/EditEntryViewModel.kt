package com.macrotrack.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Source
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
import com.macrotrack.domain.usecase.log.UpdateLogEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class EditEntryUiState(
    val entry: LogEntry,
    val food: FoodItem,
    val isSaving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDailyLogUseCase: GetDailyLogUseCase,
    private val updateLogEntryUseCase: UpdateLogEntryUseCase,
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<String>("entryId")?.toLongOrNull() ?: 0L
    private val dateIso: String = savedStateHandle.get<String>("dateIso")
        ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val targetDate: LocalDate =
        runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())

    private val _entry = MutableStateFlow<LogEntry?>(null)

    val uiState: StateFlow<EditEntryUiState?> = _entry
        .flatMapLatest { entry ->
            if (entry == null) {
                flowOf(null)
            } else {
                val per100 = if (entry.portionG > 0f) entry.macros * (100f / entry.portionG) else entry.macros
                val food = FoodItem(
                    id = entry.foodItemId ?: 0L,
                    source = Source.USER,
                    name = entry.name,
                    brand = entry.brand,
                    defaultPortionG = entry.portionG,
                    defaultPortionLabel = entry.portionLabel,
                    macroPer100g = per100,
                )
                flowOf(EditEntryUiState(entry = entry, food = food))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            getDailyLogUseCase(targetDate).collect { entries ->
                val match = entries.firstOrNull { it.id == entryId }
                if (match != null && _entry.value == null) {
                    _entry.value = match
                }
            }
        }
    }

    fun confirmPortion(portionG: Float, portionLabel: String?) {
        val entry = _entry.value ?: return
        viewModelScope.launch {
            updateLogEntryUseCase(entry, portionG, portionLabel)
        }
    }
}
