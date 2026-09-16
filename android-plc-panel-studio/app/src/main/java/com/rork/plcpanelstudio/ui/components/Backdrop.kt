package com.rork.plcpanelstudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.plcpanelstudio.ui.theme.Line

/** Graph-paper backdrop used behind canvases and panel previews. */
@Composable
fun GridBackdrop(
    modifier: Modifier = Modifier,
    cell: Dp = 24.dp,
    lineColor: Color = Line.copy(alpha = 0.55f)
) {
    Canvas(modifier = modifier) {
        val step = cell.toPx()
        if (step <= 0f) return@Canvas
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += step
        }
    }
}
