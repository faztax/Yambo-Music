package it.fast4x.rimusic.extensions.visualizer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.extensions.visualizer.audio.VisualizerData
import it.fast4x.rimusic.ui.styling.LocalAppearance


@Composable
fun StackedBarVisualizer(
    modifier: Modifier,
    data: VisualizerData,
    barCount: Int,
    maxStackCount: Int = 32
) {
    val (colorPalette) = LocalAppearance.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    Row(modifier.onSizeChanged { size = it }) {
        val viewportWidth = size.width.toFloat()
        val viewportHeight = size.height.toFloat()
        val padding = LocalDensity.current.run { 1.dp.toPx() }

        val nodes = computeStackedBarPoints(
            resampled = data.resample(barCount),
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            barCount = barCount,
            maxStackCount = maxStackCount,
            horizontalPadding = padding,
            verticalPadding = padding,
        )
            .mapIndexed { index, point ->
            if (index % 4 == 0)
                PathNode.MoveTo(point.x(), point.y())
            else
                PathNode.LineTo(point.x(), point.y())
        }

        val vectorPainter = rememberVectorPainter(
            defaultWidth = viewportWidth.dp,
            defaultHeight = viewportHeight.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            autoMirror = false
        ) { vw, vh ->
            Path(
                fill = Brush.linearGradient(
                    //listOf(Color.Red, Color.Yellow, Color.Green),
                    listOf(colorPalette.accent, colorPalette.text, colorPalette.background0),
                    start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)
                ),
                pathData = nodes
            )
        }
        Image(
            painter = vectorPainter,
            contentDescription = null
        )
    }
}

/*
@Preview
@Composable
fun EqualizerPreview() {
    val data = IntArray(32) { ((sin(it.toDouble())+1) * 128).toInt() }
    BarEqualizer(
        Modifier
            .fillMaxSize()
            .background(Color.Magenta),
        VisualizerData(data)
    )
}*/