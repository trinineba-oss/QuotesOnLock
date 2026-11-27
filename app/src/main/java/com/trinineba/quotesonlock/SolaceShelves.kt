package com.trinineba.quotesonlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Solace's shelves: short sideways rows that keep the page to about a screen and a half. Each card is the same size
 * and style, so the page reads as one calm list of choices rather than many kinds of block.
 */

private val SHELF_W = 140.dp
private val SHELF_H = 100.dp

/** One card on a shelf: a title, a short line, and a footnote (minutes) in the accent colour. */
@Composable
private fun ShelfCard(title: String, line: String, foot: String?, onClick: () -> Unit) {
    SoftCard(onClick = onClick, modifier = Modifier.width(SHELF_W).height(SHELF_H)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (foot != null) Text(foot, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Guided sessions read aloud and the practices from the wise, on one shelf. */
@Composable
fun GuidedShelf(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val guides = remember { runCatching { Guides.all(context) }.getOrDefault(emptyList()) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(guides.size, key = { "g-" + guides[it].key }) { i ->
            val g = guides[i]
            ShelfCard(g.title, g.subtitle, "${g.shortMinutes} or ${g.longMinutes} min") { onOpen("guide:" + g.key) }
        }
        items(Practices.all.size, key = { "p-" + Practices.all[it].key }) { i ->
            val p = Practices.all[i]
            ShelfCard(p.title, p.subtitle, "About ${p.minutes} min") { onOpen("practice:" + p.key) }
        }
    }
}

/** The quick tools: ground yourself, look at a thought, put the worry down. */
@Composable
fun QuickToolsShelf(worries: Int, onOpen: (String) -> Unit) {
    val tools = listOf(
        Triple("Ground yourself", "Five things you see…", "ground"),
        Triple("Look at a thought", "What would you tell a friend?", "reframe"),
        Triple("Put the worry down", if (worries == 0) "Write it now, look at it later" else "$worries kept for later", "worry"),
    )
    // Only three, so they share the width instead of running off the edge.
    androidx.compose.foundation.layout.Row(
        Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tools.forEach { (title, line, key) ->
            SoftCard(onClick = { onOpen(key) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Slow looking: a shelf of the paintings themselves. */
@Composable
fun LookingShelf(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scenes = remember { runCatching { Looking.all(context) }.getOrDefault(emptyList()) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(scenes.size, key = { scenes[it].key }) { i ->
            val s = scenes[i]
            Box(
                Modifier.width(SHELF_W).height(SHELF_H).clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Look at ${s.title}") { onOpen(s.key) },
            ) {
                Image(painterResource(Looking.image(s.key)), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(s.title, style = MaterialTheme.typography.labelLarge.copy(shadow = null), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${s.minutes} min", style = MaterialTheme.typography.labelSmall.copy(shadow = null), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
