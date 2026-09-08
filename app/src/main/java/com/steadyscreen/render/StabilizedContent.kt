package com.steadyscreen.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.steadyscreen.stabilization.StabilizationTransform

@Composable
fun StabilizedContent(
    transform: () -> StabilizationTransform,
    overscanScale: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // Read frame state in the layer phase: no text recomposition or layout per frame.
                val frame = transform()
                translationX = frame.translationX
                translationY = frame.translationY
                scaleX = overscanScale
                scaleY = overscanScale
            },
            content = content,
        )
    }
}
