package com.macrotrack.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.parser.ParsedNutritionLabel
import com.macrotrack.domain.usecase.food.AddUserFoodUseCase
import com.macrotrack.domain.usecase.food.GetRecommendationsUseCase
import com.macrotrack.domain.usecase.food.LookupBarcodeUseCase
import com.macrotrack.domain.usecase.food.SearchFoodUseCase
import com.macrotrack.domain.usecase.log.AddLogEntryUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class AddMode { SEARCH, BARCODE, LABEL, QUICK_ADD }

data class QuickAddDraft(
    val ean: String? = null,
    val name: String = "",
    val brand: String = "",
    val portionLabel: String = "",
    val portionG: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = ""
)

data class AddUiState(
    val date: LocalDate,
    val dateIso: String,
    val sections: List<Section>,
    val targetSectionId: Long,
    val mode: AddMode,
    val query: String,
    val results: List<FoodItem>,
    val pendingFood: FoodItem?,
    val quickAddDraft: QuickAddDraft,
    val message: String?
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val searchFoodUseCase: SearchFoodUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val addLogEntryUseCase: AddLogEntryUseCase,
    private val lookupBarcodeUseCase: LookupBarcodeUseCase,
    private val addUserFoodUseCase: AddUserFoodUseCase
) : ViewModel() {

    private val initialDateIso: String =
        savedStateHandle.get<String>("date") ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val initialSectionId: Long = savedStateHandle.get<String>("sectionId")?.toLongOrNull() ?: 0L

    private val _date = kotlinx.coroutines.flow.MutableStateFlow(
        runCatching { LocalDate.parse(initialDateIso) }.getOrDefault(LocalDate.now())
    )
    private val _targetSectionId = kotlinx.coroutines.flow.MutableStateFlow(initialSectionId)
    private val _mode = kotlinx.coroutines.flow.MutableStateFlow(AddMode.SEARCH)
    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    private val _pendingFood = kotlinx.coroutines.flow.MutableStateFlow<FoodItem?>(null)
    private val _quickAddDraft = kotlinx.coroutines.flow.MutableStateFlow(QuickAddDraft())
    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val _sections: StateFlow<List<Section>> = getSectionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _results: StateFlow<List<FoodItem>> = _query
        .debounce(300)
        .combine(_targetSectionId) { q, sectionId -> q to sectionId }
        .flatMapLatest { (q, sectionId) ->
            if (q.isBlank()) {
                flow { emit(getRecommendationsUseCase.getRecommendations(sectionId)) }
            } else {
                searchFoodUseCase(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AddUiState> = combine(
        combine(_sections, _targetSectionId, _mode, _query, _results) {
                sections, sectionId, mode, query, results ->

            Five(sections, sectionId, mode, query, results)
        },
        combine(_pendingFood, _quickAddDraft, _message) { pending, draft, message ->
            Three(pending, draft, message)
        }
    ) { a, b ->
        AddUiState(
            date = _date.value,
            dateIso = initialDateIso,
            sections = a.sections,
            targetSectionId = a.sectionId,
            mode = a.mode,
            query = a.query,
            results = a.results,
            pendingFood = b.pending,
            quickAddDraft = b.draft,
            message = b.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddUiState(
        date = _date.value,
        dateIso = initialDateIso,
        sections = emptyList(),
        targetSectionId = initialSectionId,
        mode = AddMode.SEARCH,
        query = "",
        results = emptyList(),
        pendingFood = null,
        quickAddDraft = QuickAddDraft(),
        message = null
    ))

    fun setMode(mode: AddMode) {
        _mode.value = mode
        _message.value = null
    }

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun setTargetSection(sectionId: Long) {
        _targetSectionId.value = sectionId
    }

    /** Called when a food is tapped in search / recommendations / barcode / label. */
    fun selectFood(food: FoodItem) {
        _pendingFood.value = food
        _message.value = null
    }

    /** Confirms the portion and writes the log entry, then returns to search. */
    fun confirmPortion(portionG: Float, portionLabel: String?) {
        val food = _pendingFood.value ?: return
        launchAdd(food, portionG, portionLabel)
        _pendingFood.value = null
        _query.value = ""
        _mode.value = AddMode.SEARCH
        _message.value = null
    }

    fun backFromPortion() {
        _pendingFood.value = null
    }

    private fun launchAdd(food: FoodItem, portionG: Float, portionLabel: String?) {
        viewModelScope.launch {
            addLogEntryUseCase(
                food = food,
                portionG = portionG,
                portionLabel = portionLabel,
                date = _date.value,
                sectionId = _targetSectionId.value,
                sortOrder = 0
            )
        }
    }

    fun onBarcodeScanned(ean: String) {
        viewModelScope.launch {
            val food = lookupBarcodeUseCase(ean)
            if (food != null) {
                _pendingFood.value = food
            } else {
                _quickAddDraft.value = QuickAddDraft(ean = ean)
                _mode.value = AddMode.QUICK_ADD
                _message.value = "Barcode $ean not found — add it manually"
            }
        }
    }

    fun onLabelParsed(parsed: ParsedNutritionLabel) {
        val draft = QuickAddDraft(
            name = "Scanned food",
            portionG = parsed.servingSizeG?.let { "%.1f".format(it) } ?: "",
            portionLabel = parsed.servingLabel ?: "",
            kcal = parsed.per100?.kcal?.let { "%.0f".format(it) } ?: "",
            protein = parsed.per100?.protein?.let { "%.1f".format(it) } ?: "",
            carbs = parsed.per100?.carbs?.let { "%.1f".format(it) } ?: "",
            fat = parsed.per100?.fat?.let { "%.1f".format(it) } ?: ""
        )
        _quickAddDraft.value = draft
        _mode.value = AddMode.QUICK_ADD
    }

    fun updateQuickAddDraft(draft: QuickAddDraft) {
        _quickAddDraft.value = draft
    }

    /** Saves a user food and opens the portion screen for it. */
    fun submitQuickAdd() {
        val draft = _quickAddDraft.value
        val food = FoodItem(
            id = 0,
            source = com.macrotrack.domain.model.Source.USER,
            sourceId = null,
            ean = draft.ean?.takeIf { it.isNotBlank() },
            brand = draft.brand.takeIf { it.isNotBlank() },
            name = draft.name,
            defaultPortionG = draft.portionG.toFloatOrNull(),
            defaultPortionLabel = draft.portionLabel.takeIf { it.isNotBlank() },
            macroPer100g = Macros(
                kcal = draft.kcal.toFloatOrNull() ?: 0f,
                proteinG = draft.protein.toFloatOrNull() ?: 0f,
                carbsG = draft.carbs.toFloatOrNull() ?: 0f,
                fatG = draft.fat.toFloatOrNull() ?: 0f
            )
        )
        viewModelScope.launch {
            val saved = addUserFoodUseCase(food)
            _pendingFood.value = saved
            _quickAddDraft.value = QuickAddDraft()
            _message.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

private data class Five(
    val sections: List<Section>,
    val sectionId: Long,
    val mode: AddMode,
    val query: String,
    val results: List<FoodItem>
)

private data class Three(
    val pending: FoodItem?,
    val draft: QuickAddDraft,
    val message: String?
)
