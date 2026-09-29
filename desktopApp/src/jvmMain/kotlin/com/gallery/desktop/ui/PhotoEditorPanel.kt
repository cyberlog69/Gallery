package com.gallery.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.model.ImageEditState
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroBorderGradient
import com.gallery.desktop.ui.theme.AeroPillGradient
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.gallery.core.model.AspectRatioPreset

@Composable
fun PhotoEditorPanel(
    editState: ImageEditState,
    onEditChange: (ImageEditState) -> Unit,
    onReset: () -> Unit,
    onSaveCopy: () -> Unit,
    onSaveInPlace: (() -> Unit)? = null,
    onClose: () -> Unit,
    isAeroTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(20.dp)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .width(340.dp)
            .then(
                if (isAeroTheme) {
                    Modifier
                        .shadow(elevation = 20.dp, shape = panelShape, spotColor = Color(0x6600C3FF))
                        .clip(panelShape)
                        .background(AeroPillGradient)
                        .border(BorderStroke(1.2.dp, AeroBorderGradient), panelShape)
                } else {
                    Modifier
                        .clip(panelShape)
                        .background(PicasaPillBackground)
                        .border(BorderStroke(1.dp, Color(0x33FFFFFF)), panelShape)
                }
            )
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isAeroTheme) AeroAccent else PicasaAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Image Adjustments",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PicasaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Aspect Ratio Crop Presets
            Text(
                text = "Crop Preset",
                color = PicasaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    AspectRatioPreset.FREEFORM to "Free",
                    AspectRatioPreset.SQUARE to "1:1",
                    AspectRatioPreset.PORTRAIT to "4:5",
                    AspectRatioPreset.LANDSCAPE to "16:9",
                    AspectRatioPreset.CLASSIC to "3:2"
                ).forEach { (preset, label) ->
                    val isSelected = if (preset == AspectRatioPreset.FREEFORM) {
                        editState.cropRect == null
                    } else {
                        editState.cropRect != null
                    }
                    OutlinedButton(
                        onClick = {
                            val rect = preset.calculateRect(1920, 1080)
                            onEditChange(editState.copy(cropRect = if (preset == AspectRatioPreset.FREEFORM) null else rect))
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(label, fontSize = 10.sp, color = Color.White, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Exposure Slider
            AdjustmentSlider(
                title = "Exposure",
                value = editState.exposure,
                range = -2.0f..2.0f,
                formattedValue = String.format("%+.1f EV", editState.exposure),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(exposure = it)) }
            )

            // Contrast Slider
            AdjustmentSlider(
                title = "Contrast",
                value = editState.contrast,
                range = 0.5f..2.0f,
                formattedValue = String.format("%.2fx", editState.contrast),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(contrast = it)) }
            )

            // Highlights Slider
            AdjustmentSlider(
                title = "Highlights",
                value = editState.highlights,
                range = -1.0f..1.0f,
                formattedValue = String.format("%+.1f", editState.highlights),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(highlights = it)) }
            )

            // Shadows Slider
            AdjustmentSlider(
                title = "Shadows",
                value = editState.shadows,
                range = -1.0f..1.0f,
                formattedValue = String.format("%+.1f", editState.shadows),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(shadows = it)) }
            )

            // Saturation Slider
            AdjustmentSlider(
                title = "Saturation",
                value = editState.saturation,
                range = 0.0f..2.0f,
                formattedValue = String.format("%.2fx", editState.saturation),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(saturation = it)) }
            )

            // Temperature Slider
            AdjustmentSlider(
                title = "Temperature",
                value = editState.temperature,
                range = -1.0f..1.0f,
                formattedValue = if (editState.temperature < 0f) "Cool" else if (editState.temperature > 0f) "Warm" else "Neutral",
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(temperature = it)) }
            )

            // Tint Slider
            AdjustmentSlider(
                title = "Tint",
                value = editState.tint,
                range = -1.0f..1.0f,
                formattedValue = if (editState.tint < 0f) "Green" else if (editState.tint > 0f) "Magenta" else "Neutral",
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(tint = it)) }
            )

            // Straighten Slider (-45° to +45°)
            AdjustmentSlider(
                title = "Straighten",
                value = editState.straightenAngle,
                range = -45.0f..45.0f,
                formattedValue = String.format("%+.1f°", editState.straightenAngle),
                isAeroTheme = isAeroTheme,
                onValueChange = { onEditChange(editState.copy(straightenAngle = it)) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Tools Row (Flip, Rotate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEditChange(editState.copy(flipHorizontal = !editState.flipHorizontal)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Flip, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Flip", color = Color.White, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val nextRot = (editState.rotationDegrees + 90f) % 360f
                        onEditChange(editState.copy(rotationDegrees = nextRot))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rotate", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(0.9f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = PicasaTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Reset", color = PicasaTextSecondary, fontSize = 11.sp)
                }

                if (onSaveInPlace != null) {
                    OutlinedButton(
                        onClick = onSaveInPlace,
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save (.bak)", color = Color.White, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onSaveCopy,
                    modifier = Modifier.weight(1.1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAeroTheme) AeroAccent else PicasaAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    isAeroTheme: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = PicasaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                text = formattedValue,
                color = if (isAeroTheme) AeroAccent else PicasaAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = if (isAeroTheme) AeroAccent else PicasaAccent,
                activeTrackColor = if (isAeroTheme) AeroAccent else PicasaAccent,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
