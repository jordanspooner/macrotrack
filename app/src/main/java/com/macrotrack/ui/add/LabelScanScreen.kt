package com.macrotrack.ui.add

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.macrotrack.domain.parser.ConsensusField
import com.macrotrack.domain.parser.LabelConsensus
import com.macrotrack.domain.parser.LabelParser
import com.macrotrack.domain.parser.OcrElement
import com.macrotrack.domain.parser.ParsedNutritionLabel
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.Executors

@Composable
fun LabelScanScreen(onLabelConfirmed: (ParsedNutritionLabel) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val parser = remember { LabelParser() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var consensus by remember { mutableStateOf(LabelConsensus()) }
    val textFlow = remember { MutableSharedFlow<ParsedNutritionLabel>(extraBufferCapacity = 64) }
    val scope = rememberCoroutineScope()

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Camera permission is required to scan labels.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        }
        return
    }

    // Only the cheap Compose state update runs on the main thread; the
    // O(n²) parsing happens on Dispatchers.Default (see processText), so a
    // busy label can never stall input dispatch.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        textFlow.collect { label ->
            consensus = consensus.accept(label)
        }
    }

    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val ex = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(ex) { imageProxy ->
                processText(imageProxy, recognizer, parser, scope) { label -> textFlow.tryEmit(label) }
            }
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            ex.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    // Pretty overlay: live consensus, not raw text.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        // Single source of truth: the live MAP consensus shown everywhere.
        val label = consensus.toParsedLabel()

        // Top: live energy + serving readouts (per 100 g), mirrors the bottom panel.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val kcalValue = label.per100?.kcal
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = kcalValue?.let { "%.0f".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (consensus.kcal.confirmed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (consensus.kcalDerived) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "Derived",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                    Text(
                        "kcal per 100g/ml",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val servingValue = label.servingSizeG
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = servingValue?.let { "%.0f".format(it) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (consensus.servingG.confirmed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "serving (g/ml)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom: translucent panel with pie on the side and macros below.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Macros",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "per 100g/ml",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MacroPieChart(
                        macros = label.toMacros(),
                        modifier = Modifier
                            .size(104.dp)
                            .padding(4.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FieldRow("Protein", label.per100?.protein, consensus.protein.confirmed, consensus.protein.progress, suffix = "g") { "%.1f".format(it) }
                        FieldRow("Carbs", label.per100?.carbs, consensus.carbs.confirmed, consensus.carbs.progress, suffix = "g") { "%.1f".format(it) }
                        FieldRow("Fat", label.per100?.fat, consensus.fat.confirmed, consensus.fat.progress, suffix = "g") { "%.1f".format(it) }
                    }
                }

                Button(
                    onClick = { if (consensus.isReady) onLabelConfirmed(consensus.toParsedLabel()) },
                    enabled = consensus.isReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text("Use this label")
                }
                if (!consensus.isReady) {
                    Text(
                        "Refining… values lock once energy + all 3 macros agree",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: Float?,
    confirmed: Boolean,
    progress: Float,
    suffix: String = "",
    format: (Float) -> String = { it.toString() }
) {
    val display = when {
        value != null -> "${format(value)}${if (suffix.isNotEmpty()) " $suffix" else ""}"
        else -> "—"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    display,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (confirmed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (confirmed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Confirmed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp)
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

private fun ParsedNutritionLabel.toMacros(): com.macrotrack.domain.model.Macros {
    return com.macrotrack.domain.model.Macros(
        kcal = per100?.kcal ?: perServing?.kcal ?: 0f,
        proteinG = per100?.protein ?: perServing?.protein ?: 0f,
        carbsG = per100?.carbs ?: perServing?.carbs ?: 0f,
        fatG = per100?.fat ?: perServing?.fat ?: 0f
    )
}

private fun processText(
    imageProxy: ImageProxy,
    recognizer: TextRecognizer,
    parser: LabelParser,
    scope: CoroutineScope,
    onLabel: (ParsedNutritionLabel) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        // ML Kit callbacks run on the main thread (its default executor), NOT the
        // analyzer executor `ex`. Routing them through `ex` previously crashed on
        // teardown because onDispose() shuts `ex` down while in-flight process()
        // tasks were still completing (ML Kit double-completed its internal task).
        // Only the O(n²) parse is moved to Dispatchers.Default; the coroutine
        // scope is cancelled on dispose, so no parse runs after teardown.
        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                val elements = mutableListOf<OcrElement>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val box = element.boundingBox
                            if (box != null) {
                                elements.add(
                                    OcrElement(
                                        text = element.text,
                                        left = box.left,
                                        top = box.top,
                                        right = box.right,
                                        bottom = box.bottom
                                    )
                                )
                            }
                        }
                    }
                }
                scope.launch(Dispatchers.Default) {
                    onLabel(parser.parseStructured(elements))
                }
            }
            .addOnFailureListener { imageProxy.close() }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}
