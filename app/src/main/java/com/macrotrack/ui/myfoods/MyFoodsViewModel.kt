package com.macrotrack.ui.myfoods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.domain.usecase.food.DeleteUserFoodUseCase
import com.macrotrack.domain.usecase.food.SearchUserFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyFoodsViewModel @Inject constructor(
    private val searchUserFoodsUseCase: SearchUserFoodsUseCase,
    private val deleteUserFoodUseCase: DeleteUserFoodUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val _foods: StateFlow<List<com.macrotrack.domain.model.FoodItem>> = _query
        .debounce(300)
        .flatMapLatest { searchUserFoodsUseCase(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<MyFoodsUiState> = combine(_query, _foods) { query, foods ->
        MyFoodsUiState(query = query, foods = foods)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyFoodsUiState())

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch {
            deleteUserFoodUseCase(id)
        }
    }
}
