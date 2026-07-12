package com.macrotrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun AddFoodBottomBar(
    onSearchClick: () -> Unit,
    onLabelScanClick: () -> Unit,
    onBarcodeScanClick: () -> Unit,
    onQuickAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search Foods")
            }
            // Using default Add icon for label scan as placeholder for custom icon
            IconButton(onClick = onLabelScanClick) {
                Icon(Icons.Default.Add, contentDescription = "Label Scan")
            }
            // Using default Add icon for barcode as placeholder
            IconButton(onClick = onBarcodeScanClick) {
                Icon(Icons.Default.Add, contentDescription = "Barcode Scan")
            }
            IconButton(onClick = onQuickAddClick) {
                Icon(Icons.Default.Create, contentDescription = "Quick Add")
            }
        }
    }
}
