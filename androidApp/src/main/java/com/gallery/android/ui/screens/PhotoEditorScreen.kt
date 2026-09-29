package com.gallery.android.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.edit.AndroidImageEditor
import com.gallery.core.model.AspectRatioPreset
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTab(val title: String, val icon: ImageVector) {
    ADJUST("Adjust", Icons.Default.Tune),
    CROP("Crop", Icons.Default.Crop),
    TRANSFORM("Transform", Icons.AutoMirrored.Filled.RotateRight)
}

enum class AdjustTool(val title: String, val icon: ImageVector) {
    EXPOSURE("Exposure", Icons.Default.Brightness6),
    CONTRAST("Contrast", Icons.Default.Contrast),
    SATURATION("Saturation", Icons.Default.InvertColors),
    HIGHLIGHTS("Highlights", Icons.Default.WbSunny),
    SHADOWS("Shadows", Icons.Default.WbSunny),
    STRAIGHTEN("Straighten", Icons.Default.Straighten)
}

@Composable
fun PhotoEditorScreen(
    item: MediaItem,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editState by remember { mutableStateOf(ImageEditState.DEFAULT) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isHoldingOriginal by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(EditorTab.ADJUST) }
    var selectedAdjustTool by remember { mutableStateOf(AdjustTool.EXPOSURE) }
    var showSaveMenu by remember { mutableStateOf(false) }

    // Load initial bitmap
    LaunchedEffect(item.uri) {
        isLoading = true
        val bmp = AndroidImageEditor.loadBitmap(context, item.uri, maxDimension = 1440)
        originalBitmap = bmp
        editedBitmap = bmp
        isLoading = false
    }

    // Recompute edited bitmap reactively when editState changes
    LaunchedEffect(editState, originalBitmap) {
        val src = originalBitmap ?: return@LaunchedEffect
        if (!editState.isEdited) {
            editedBitmap = src
        } else {
            withContext(Dispatchers.Default) {
                val processed = AndroidImageEditor.applyEdit(src, editState)
                withContext(Dispatchers.Main) {
                    editedBitmap = processed
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose, enabled = !isSaving) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Compare button: hold to preview untouched original
                    IconButton(
                        onClick = { /* Tap toggles briefly or use pointerInput below */ },
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingOriginal = true
                                    tryAwaitRelease()
                                    isHoldingOriginal = false
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Hold to compare",
                            tint = if (isHoldingOriginal) Color(0xFF389BF2) else Color.White
                        )
                    }

                    // Reset button (enabled only when modified)
                    if (editState.isEdited) {
                        IconButton(onClick = { editState = ImageEditState.DEFAULT }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset all edits",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Button with Dropdown
                    Box {
                        TextButton(
                            onClick = { showSaveMenu = true },
                            enabled = !isSaving && (originalBitmap != null)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Save",
                                    color = Color(0xFF389BF2),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSaveMenu,
                            onDismissRequest = { showSaveMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as Copy (Recommended)") },
                                onClick = {
                                    showSaveMenu = false
                                    isSaving = true
                                    scope.launch {
                                        val src = originalBitmap ?: return@launch
                                        val uri = AndroidImageEditor.saveAsCopy(
                                            context = context,
                                            sourceBitmap = src,
                                            state = editState,
                                            originalDisplayName = item.displayName
                                        )
                                        isSaving = false
                                        if (uri != null) {
                                            Toast.makeText(context, "Saved copy to Gallery", Toast.LENGTH_SHORT).show()
                                            onSaved()
                                        } else {
                                            Toast.makeText(context, "Failed to save copy", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Changes (In-Place with .bak)") },
                                onClick = {
                                    showSaveMenu = false
                                    isSaving = true
                                    scope.launch {
                                        val src = originalBitmap ?: return@launch
                                        val success = AndroidImageEditor.saveInPlace(
                                            context = context,
                                            originalItem = item,
                                            sourceBitmap = src,
                                            state = editState
                                        )
                                        isSaving = false
                                        if (success) {
                                            Toast.makeText(context, "Changes saved in-place", Toast.LENGTH_SHORT).show()
                                            onSaved()
                                        } else {
                                            Toast.makeText(context, "Could not overwrite file directly", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Image Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF389BF2))
                } else {
                    val displayBmp = if (isHoldingOriginal) originalBitmap else editedBitmap
                    if (displayBmp != null) {
                        Image(
                            bitmap = displayBmp.asImageBitmap(),
                            contentDescription = "Edited Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    if (isHoldingOriginal) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xAA000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "ORIGINAL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sub-tool Controls Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF18181E))
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    EditorTab.ADJUST -> {
                        AdjustControlPanel(
                            selectedTool = selectedAdjustTool,
                            onToolSelect = { selectedAdjustTool = it },
                            state = editState,
                            onStateChange = { editState = it }
                        )
                    }
                    EditorTab.CROP -> {
                        CropControlPanel(
                            bitmap = originalBitmap,
                            state = editState,
                            onStateChange = { editState = it }
                        )
                    }
                    EditorTab.TRANSFORM -> {
                        TransformControlPanel(
                            state = editState,
                            onStateChange = { editState = it }
                        )
                    }
                }
            }

            // Bottom Navigation Tabs (Adjust / Crop / Transform)
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF121216),
                contentColor = Color(0xFF389BF2),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color(0xFF389BF2)
                    )
                }
            ) {
                EditorTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, fontSize = 13.sp) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp)) },
                        selectedContentColor = Color(0xFF389BF2),
                        unselectedContentColor = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustControlPanel(
    selectedTool: AdjustTool,
    onToolSelect: (AdjustTool) -> Unit,
    state: ImageEditState,
    onStateChange: (ImageEditState) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Slider for currently selected tool
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedTool.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(85.dp)
            )

            when (selectedTool) {
                AdjustTool.EXPOSURE -> {
                    Slider(
                        value = state.exposure,
                        onValueChange = { onStateChange(state.copy(exposure = it)) },
                        valueRange = -2.0f..2.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = String.format("%.2f", state.exposure),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
                AdjustTool.CONTRAST -> {
                    Slider(
                        value = state.contrast,
                        onValueChange = { onStateChange(state.copy(contrast = it)) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = String.format("%.2f", state.contrast),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
                AdjustTool.SATURATION -> {
                    Slider(
                        value = state.saturation,
                        onValueChange = { onStateChange(state.copy(saturation = it)) },
                        valueRange = 0.0f..2.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = String.format("%.2f", state.saturation),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
                AdjustTool.HIGHLIGHTS -> {
                    Slider(
                        value = state.highlights,
                        onValueChange = { onStateChange(state.copy(highlights = it)) },
                        valueRange = -1.0f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = String.format("%.2f", state.highlights),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
                AdjustTool.SHADOWS -> {
                    Slider(
                        value = state.shadows,
                        onValueChange = { onStateChange(state.copy(shadows = it)) },
                        valueRange = -1.0f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = String.format("%.2f", state.shadows),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
                AdjustTool.STRAIGHTEN -> {
                    Slider(
                        value = state.straightenAngle,
                        onValueChange = { onStateChange(state.copy(straightenAngle = it)) },
                        valueRange = -45f..45f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF389BF2),
                            activeTrackColor = Color(0xFF389BF2)
                        )
                    )
                    Text(
                        text = "${state.straightenAngle.toInt()}°",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(45.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tool Picker Chips (Horizontal Scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdjustTool.entries.forEach { tool ->
                val isSelected = selectedTool == tool
                FilterChip(
                    selected = isSelected,
                    onClick = { onToolSelect(tool) },
                    label = { Text(tool.title, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(tool.icon, contentDescription = tool.title, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF389BF2),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xFF24242C),
                        labelColor = Color.LightGray,
                        iconColor = Color.LightGray
                    )
                )
            }
        }
    }
}

@Composable
private fun CropControlPanel(
    bitmap: Bitmap?,
    state: ImageEditState,
    onStateChange: (ImageEditState) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Aspect Ratio Presets",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatioPreset.entries.forEach { preset ->
                val isSelected = when (preset) {
                    AspectRatioPreset.FREEFORM, AspectRatioPreset.ORIGINAL -> state.cropRect == null
                    else -> state.cropRect != null
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val w = bitmap?.width ?: 1000
                        val h = bitmap?.height ?: 1000
                        if (preset == AspectRatioPreset.FREEFORM || preset == AspectRatioPreset.ORIGINAL) {
                            onStateChange(state.copy(cropRect = null))
                        } else {
                            val rect = preset.calculateRect(w, h)
                            onStateChange(state.copy(cropRect = rect))
                        }
                    },
                    label = { Text(preset.displayName, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF389BF2),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF24242C),
                        labelColor = Color.LightGray
                    )
                )
            }
        }
    }
}

@Composable
private fun TransformControlPanel(
    state: ImageEditState,
    onStateChange: (ImageEditState) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rotate 90
        IconButton(
            onClick = {
                val nextRot = (state.rotationDegrees + 90f) % 360f
                onStateChange(state.copy(rotationDegrees = nextRot))
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate 90",
                    tint = Color.White
                )
                Text("Rotate", color = Color.LightGray, fontSize = 11.sp)
            }
        }

        // Flip Horizontal
        IconButton(
            onClick = { onStateChange(state.copy(flipHorizontal = !state.flipHorizontal)) }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Flip Horizontal",
                    tint = if (state.flipHorizontal) Color(0xFF389BF2) else Color.White
                )
                Text("Flip H", color = Color.LightGray, fontSize = 11.sp)
            }
        }

        // Flip Vertical
        IconButton(
            onClick = { onStateChange(state.copy(flipVertical = !state.flipVertical)) }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Flip Vertical",
                    tint = if (state.flipVertical) Color(0xFF389BF2) else Color.White
                )
                Text("Flip V", color = Color.LightGray, fontSize = 11.sp)
            }
        }
    }
}
