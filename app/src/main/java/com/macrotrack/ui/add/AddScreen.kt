package com.macrotrack.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.domain.model.Section
import com.macrotrack.ui.theme.brandPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onBack: () -> Unit,
    viewModel: AddViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val modes = AddMode.values()
    val selectedTab = modes.indexOf(uiState.mode).coerceAtLeast(0)
    val pendingFood = uiState.pendingFood

    val relDate = when (uiState.date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> uiState.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    val sectionName =
        uiState.sections.find { it.id == uiState.targetSectionId }?.name ?: "Dinner"

    var sectionMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(relDate, style = MaterialTheme.typography.titleMedium)
                        Text(
                            sectionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            uiState.message?.let { msg ->
                Snackbar(
                    action = { Text("OK"); viewModel.clearMessage() },
                    modifier = Modifier.padding(16.dp)
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Section selector
            SectionSelector(
                sections = uiState.sections,
                selectedId = uiState.targetSectionId,
                expanded = sectionMenuExpanded,
                onExpandedChange = { sectionMenuExpanded = it },
                onSectionSelected = { viewModel.setTargetSection(it) }
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab),
                        height = 3.dp,
                        color = brandPrimary()
                    )
                }
            ) {
                modes.forEachIndexed { index, mode ->
                    val selected = selectedTab == index
                    Tab(
                        selected = selected,
                        onClick = { viewModel.setMode(mode) },
                        text = {
                            Text(
                                mode.label,
                                color = if (selected) brandPrimary() else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (pendingFood != null) {
                    PortionSizeScreen(
                        food = pendingFood,
                        sectionName = sectionName,
                        onConfirm = viewModel::confirmPortion,
                        onBack = viewModel::backFromPortion
                    )
                } else {
                    when (uiState.mode) {
                        AddMode.SEARCH -> SearchContent(
                            uiState = uiState,
                            onQueryChanged = viewModel::onQueryChanged,
                            onFoodSelected = viewModel::selectFood,
                            onQuickAddClick = { viewModel.setMode(AddMode.QUICK_ADD) }
                        )
                        AddMode.BARCODE -> BarcodeScanScreen(onBarcodeDetected = viewModel::onBarcodeScanned)
                        AddMode.LABEL -> LabelScanScreen(onLabelConfirmed = viewModel::onLabelParsed)
                        AddMode.QUICK_ADD -> QuickAddContent(
                            uiState = uiState,
                            onDraftChanged = viewModel::updateQuickAddDraft,
                            onSubmit = viewModel::submitQuickAdd
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionSelector(
    sections: List<Section>,
    selectedId: Long,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSectionSelected: (Long) -> Unit
) {
    val selected = sections.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = selected?.name ?: "Select section",
            onValueChange = {},
            readOnly = true,
            label = { Text("Section") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            sections.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.name) },
                    onClick = {
                        onSectionSelected(section.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

private val AddMode.label: String
    get() = when (this) {
        AddMode.SEARCH -> "Search"
        AddMode.BARCODE -> "Barcode"
        AddMode.LABEL -> "Label"
        AddMode.QUICK_ADD -> "Quick"
    }
