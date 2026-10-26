package com.motogeek.telemetry.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.ui.theme.NeonCyanText

@Composable
fun SegmentedDigitalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NeonCyanText,
    fontSize: TextUnit = 24.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = 2.sp,
            fontFeatureSettings = "tnum"
        )
    )
}
