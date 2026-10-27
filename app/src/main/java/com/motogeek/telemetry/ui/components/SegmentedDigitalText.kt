package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.ui.theme.TelemetryIceCyan
import com.motogeek.telemetry.ui.theme.TftSurfaceElevated

@Composable
fun SegmentedDigitalText(
    text: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = TelemetryIceCyan,
    fontSize: TextUnit = 24.sp,
    showGlow: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = text,
            color = color,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                letterSpacing = 1.5.sp,
                fontFeatureSettings = "tnum",
                shadow = if (showGlow) Shadow(
                    color = color.copy(alpha = 0.45f),
                    offset = Offset(0f, 0f),
                    blurRadius = 8f
                ) else null
            ),
            maxLines = 1,
            softWrap = false
        )
        if (!unit.isNullOrBlank()) {
            Text(
                text = " $unit",
                color = color.copy(alpha = 0.75f),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize.value * 0.45f).coerceAtLeast(10f).sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = (fontSize.value * 0.12f).dp),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
