package com.trinineba.quotesonlock

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** The "Soft edge" setting (0–1), read by every [SoftCard] in the app. */
val LocalCardSoftness = compositionLocalOf { 0f }

/**
 * A card that follows the Soft edge setting: at 0 it is an ordinary Material card; above that its
 * background fades out toward the edges (a vignette) instead of ending in a clean line.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val softness = LocalCardSoftness.current
    if (softness < 0.03f) {
        if (onClick != null) Card(onClick = onClick, modifier = modifier, content = content)
        else Card(modifier = modifier, content = content)
        return
    }
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val soft = modifier.drawBehind { drawSoftBackground(color, softness) }
    if (onClick != null) Card(onClick = onClick, modifier = soft, colors = colors, content = content)
    else Card(modifier = soft, colors = colors, content = content)
}

/** Nested rounded rectangles whose alphas add up to a ramp from transparent at the edge to [color]. */
private fun DrawScope.drawSoftBackground(color: Color, softness: Float) {
    // The fade stays inside the card's 16dp padding so text is never over the thin part.
    val feather = softness * 14.dp.toPx()
    val radius = 12.dp.toPx()
    val layers = 14
    var covered = 0f
    for (i in 0 until layers) {
        val x = (i + 1f) / layers
        val target = color.alpha * (x * x * (3f - 2f * x))
        val a = if (covered >= 1f) 0f else (1f - (1f - target) / (1f - covered)).coerceIn(0f, 1f)
        covered = target
        if (a <= 0f) continue
        val inset = feather * i / (layers - 1)
        val r = (radius - inset * 0.5f).coerceAtLeast(2f)
        drawRoundRect(
            color = color.copy(alpha = a),
            topLeft = Offset(inset, inset),
            size = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(r, r),
        )
    }
}
