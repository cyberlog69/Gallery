package com.gallery.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gallery.core.print.DesktopPrintEngine
import com.gallery.core.print.PrintLayoutPreset
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroBorderGradient
import com.gallery.desktop.ui.theme.AeroPillGradient
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary
import java.awt.image.BufferedImage

@Composable
fun PrintDialog(
    rawImage: BufferedImage?,
    onDismiss: () -> Unit,
    isAeroTheme: Boolean = true
) {
    if (rawImage == null) return

    var selectedPreset by remember { mutableStateOf(PrintLayoutPreset.FULL_PAGE) }
    val dialogShape = RoundedCornerShape(20.dp)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .then(
                    if (isAeroTheme) {
                        Modifier
                            .shadow(elevation = 24.dp, shape = dialogShape, spotColor = Color(0x6600C3FF))
                            .clip(dialogShape)
                            .background(AeroPillGradient)
                            .border(BorderStroke(1.2.dp, AeroBorderGradient), dialogShape)
                    } else {
                        Modifier
                            .clip(dialogShape)
                            .background(PicasaPillBackground)
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), dialogShape)
                    }
                )
                .padding(24.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = if (isAeroTheme) AeroAccent else PicasaAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Print Photos",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PicasaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Print Layout",
                    color = PicasaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Layout Presets Radio List
                PrintLayoutPreset.entries.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedPreset = preset }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedPreset = preset },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = if (isAeroTheme) AeroAccent else PicasaAccent,
                                unselectedColor = Color(0x66FFFFFF)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = preset.displayName,
                                color = if (isSelected) Color.White else PicasaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = preset.description,
                                color = PicasaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            DesktopPrintEngine.printImage(rawImage, selectedPreset)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAeroTheme) AeroAccent else PicasaAccent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print...", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
