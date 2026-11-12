package com.trinineba.quotesonlock

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

/**
 * Small portraits of the authors (assets/portraits/<slug>.webp), public-domain pictures from the Library of
 * Congress, cropped round the face and toned sepia; credits in assets/portraits/credits.txt (`slug|title|date|url`).
 * Authors without one (in-copyright photographs, or no true likeness) get their initials instead.
 */
object Portraits {
    private val cache = HashMap<String, ImageBitmap?>()
    @Volatile private var credits: Map<String, String>? = null

    /** The file name for an author: accents dropped, lower case, anything else becomes "_". */
    fun slug(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("[^\\p{ASCII}]"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    @Synchronized
    fun of(context: Context, name: String): ImageBitmap? {
        val s = slug(name)
        return cache.getOrPut(s) {
            runCatching {
                context.assets.open("portraits/$s.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    fun credit(context: Context, name: String): String? {
        val map = credits ?: runCatching {
            context.assets.open("portraits/credits.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                    val f = line.split('|')
                    f[0] to listOfNotNull(f.getOrNull(1)?.ifBlank { null }, f.getOrNull(2)?.ifBlank { null }).joinToString(", ")
                }
            }
        }.getOrDefault(emptyMap()).also { credits = it }
        return map[slug(name)]
    }

    /** "Charles Darwin" → "CD", "Erasmus" → "E", "W. E. B. Du Bois" → "WB". */
    fun initials(name: String): String {
        val words = name.split(' ', '.', '-').filter { it.isNotBlank() && it.first().isUpperCase() && it != "The" }
        if (words.isEmpty()) return name.take(1).uppercase()
        return (words.first().take(1) + if (words.size > 1) words.last().take(1) else "").uppercase()
    }
}

/** The author's portrait in a circle, or their initials; tap a portrait to see it larger with its credit. */
@Composable
fun AuthorPortrait(name: String, size: Dp = 76.dp) {
    val context = LocalContext.current
    val picture = remember(name) { Portraits.of(context, name) }
    var open by remember { mutableStateOf(false) }
    val ring = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = "Portrait of $name",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).border(ring, CircleShape).clickable(onClickLabel = "See the portrait larger") { open = true },
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Portraits.initials(name),
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = (size.value * 0.34f).sp,
            )
        }
    }
    if (open && picture != null) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            title = { Text(name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = picture, contentDescription = "Portrait of $name", modifier = Modifier.size(240.dp).clip(MaterialTheme.shapes.large))
                    Text(
                        Portraits.credit(context, name).orEmpty().let { c ->
                            if ("Wikimedia" in c || "Europeana" in c) "$c." else (if (c.isBlank()) "" else "$c. ") + "Library of Congress, Prints and Photographs Division; no known restrictions on publication."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }
}
