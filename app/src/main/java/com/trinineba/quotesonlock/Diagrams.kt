package com.trinineba.quotesonlock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small diagrams for Learn's lessons, drawn in code so they are sharp at any size, follow the theme's
 * colours and cost almost nothing to ship. Each draws itself in once when it appears (about a second
 * and a half) and then stays still. Coordinates are on a 320-wide canvas of the diagram's [height].
 */
sealed class Diagram(val height: Float, val caption: String) {
    /** Lines over x and y from 0 to 1. */
    class Lines(
        val series: List<Series>,
        val marks: List<Mark> = emptyList(),
        val xLabels: Pair<String, String>? = null,
        val yLabel: String? = null,
        caption: String,
        height: Float = 200f,
    ) : Diagram(height, caption)

    class Series(val points: List<Offset>, val label: String?, val tone: Tone, val dashed: Boolean = false)
    class Mark(val x: Float, val y: Float, val label: String, val tone: Tone = Tone.GOOD, val left: Boolean = true)

    /** Bars from 0 to 1, in groups. */
    class Bars(val bars: List<Bar>, val unit: String = "", caption: String, height: Float = 190f) : Diagram(height, caption)
    class Bar(val label: String, val value: Float, val tone: Tone, val text: String? = null)

    /** A grid of cells with column and row headings. */
    class Grid(val cols: List<String>, val rows: List<String>, val cells: List<List<Cell>>, caption: String, height: Float = 210f) : Diagram(height, caption)
    class Cell(val lines: List<String>, val tone: Tone? = null, val note: String? = null)

    /** A beam with three points: two extremes and the mean between them. */
    class Scale(val left: String, val middle: String, val right: String, caption: String) : Diagram(150f, caption)

    /** Boxes joined by arrows, in a row (or round a circle). */
    class Flow(val steps: List<String>, val cycle: Boolean = false, val highlight: Int? = null, caption: String, height: Float = 0f) :
        Diagram(if (height > 0f) height else if (cycle) 250f else 60f + 52f * steps.size, caption)

    /** Causes feeding effects: [links] are (from, to, dashed) between [nodes] placed at positions. */
    class Net(val nodes: List<Pair<String, Offset>>, val links: List<Triple<Int, Int, Boolean>>, val tones: Map<Int, Tone> = emptyMap(), caption: String, height: Float = 190f) : Diagram(height, caption)

    /** A long bar with a tiny final slice: how much of a span something takes up. */
    class Timeline(val total: String, val parts: List<Pair<String, Float>>, val sliceLabel: String, caption: String) : Diagram(150f, caption)

    /** Stacked levels, widest at the bottom. */
    class Pyramid(val levels: List<String>, caption: String) : Diagram(220f, caption)

    /** Concentric rings with labels. */
    class Rings(val rings: List<String>, val centre: String, caption: String) : Diagram(230f, caption)

    /** A field of dots, some marked. */
    class Dots(val total: Int, val marked: List<Pair<Int, Tone>>, val legend: List<Pair<String, Tone>>, caption: String) : Diagram(230f, caption)

    /** One of the hand-drawn pictures. */
    class Picture(val kind: String, caption: String, height: Float = 210f) : Diagram(height, caption)
}

enum class Tone { INK, ACCENT, GOOD, BAD, MUTED }

/** Shows the diagram for [ref] (an idea or myth's title), if it has one. */
@Composable
fun LessonDiagram(ref: String) {
    val d = Diagrams.of(ref) ?: return
    DiagramView(d, ref)
}

@Composable
fun DiagramView(d: Diagram, key: String) {
    // Draws in the first time it is seen, then stays drawn (also after scrolling away and back).
    var played by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            progress.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            played = true
        }
    }
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val palette = Palette(cs.onSurface, cs.primary, Color(0xFF2E8B74), Color(0xFFB3402E), cs.onSurfaceVariant, cs.surfaceVariant, cs.outlineVariant)
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(320f / d.height)) {
                val s = size.width / 320f
                DiagramPainter(this, measurer, palette, s, progress.value).draw(d)
            }
            Text(
                d.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            )
        }
    }
}

internal class Palette(val ink: Color, val accent: Color, val good: Color, val bad: Color, val muted: Color, val soft: Color, val line: Color) {
    fun of(t: Tone): Color = when (t) {
        Tone.INK -> ink
        Tone.ACCENT -> accent
        Tone.GOOD -> good
        Tone.BAD -> bad
        Tone.MUTED -> muted
    }
}

/** Draws one diagram at draw-in progress [p] (0 to 1). */
private class DiagramPainter(val ds: DrawScope, val tm: TextMeasurer, val c: Palette, val s: Float, val p: Float) {
    fun o(x: Float, y: Float) = Offset(x * s, y * s)

    /** How far along a part is, when it starts at [from] and finishes at [to] of the whole draw-in. */
    fun part(from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

    fun text(t: String, x: Float, y: Float, color: Color, size: Float = 11.5f, align: TextAlign = TextAlign.Start, alpha: Float = 1f, bold: Boolean = false, width: Float = 0f) {
        if (alpha <= 0f) return
        val style = TextStyle(color = color.copy(alpha = color.alpha * alpha), fontSize = with(ds) { (size * s).toSp() }, textAlign = align,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        val maxW = ((if (width > 0f) width else 300f) * s).toInt().coerceAtLeast(1)
        val layout = tm.measure(t, style, constraints = Constraints(maxWidth = maxW))
        val w = layout.size.width.toFloat()
        val left = when (align) {
            TextAlign.Center -> x * s - (if (width > 0f) width * s else w) / 2f
            TextAlign.End -> x * s - (if (width > 0f) width * s else w)
            else -> x * s
        }
        ds.drawText(layout, topLeft = Offset(left, y * s - layout.size.height / 2f))
    }

    fun line(a: Offset, b: Offset, color: Color, w: Float = 2f, dashed: Boolean = false) =
        ds.drawLine(color, o(a.x, a.y), o(b.x, b.y), w * s, StrokeCap.Round, if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f * s, 5f * s)) else null)

    fun arrow(a: Offset, b: Offset, color: Color, f: Float = 1f, dashed: Boolean = false) {
        if (f <= 0f) return
        val e = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        line(a, e, color, 2f, dashed)
        if (f >= 1f) {
            val dx = b.x - a.x; val dy = b.y - a.y; val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val ux = dx / l; val uy = dy / l
            val h = 7f
            line(b, Offset(b.x - ux * h - uy * h * 0.6f, b.y - uy * h + ux * h * 0.6f), color, 2f)
            line(b, Offset(b.x - ux * h + uy * h * 0.6f, b.y - uy * h - ux * h * 0.6f), color, 2f)
        }
    }

    fun box(x: Float, y: Float, w: Float, h: Float, fill: Color, stroke: Color? = null, r: Float = 10f, alpha: Float = 1f) {
        if (alpha <= 0f) return
        ds.drawRoundRect(fill.copy(alpha = fill.alpha * alpha), o(x, y), Size(w * s, h * s), CornerRadius(r * s))
        stroke?.let { ds.drawRoundRect(it.copy(alpha = it.alpha * alpha), o(x, y), Size(w * s, h * s), CornerRadius(r * s), style = Stroke(1.5f * s)) }
    }

    fun draw(d: Diagram) = when (d) {
        is Diagram.Lines -> lines(d)
        is Diagram.Bars -> bars(d)
        is Diagram.Grid -> grid(d)
        is Diagram.Scale -> scale(d)
        is Diagram.Flow -> flow(d)
        is Diagram.Net -> net(d)
        is Diagram.Timeline -> timeline(d)
        is Diagram.Pyramid -> pyramid(d)
        is Diagram.Rings -> rings(d)
        is Diagram.Dots -> dots(d)
        is Diagram.Picture -> picture(d)
    }

    // ---- lines -----------------------------------------------------------------------------

    fun lines(d: Diagram.Lines) {
        val l = 36f; val r = 304f; val t = 18f; val b = d.height - 26f
        fun map(pt: Offset) = Offset(l + pt.x * (r - l), b - pt.y * (b - t))
        line(Offset(l, b), Offset(r, b), c.line, 1.2f)
        line(Offset(l, t), Offset(l, b), c.line, 1.2f)
        d.xLabels?.let { (a, z) ->
            text(a, l, b + 13f, c.muted, 11f)
            text(z, r, b + 13f, c.muted, 11f, TextAlign.End)
        }
        d.yLabel?.let { text(it, l + 4f, t - 6f, c.muted, 10.5f) }
        val f = part(0f, 0.75f)
        d.series.forEachIndexed { i, se ->
            val pts = se.points
            if (pts.size < 2) return@forEachIndexed
            val n = ((pts.size - 1) * f).coerceAtLeast(0f)
            val path = Path()
            val first = map(pts[0]); path.moveTo(first.x * s, first.y * s)
            var k = 1
            while (k <= n.toInt()) { val q = map(pts[k]); path.lineTo(q.x * s, q.y * s); k++ }
            if (k < pts.size && n > n.toInt()) {
                val a = map(pts[k - 1]); val bb = map(pts[k]); val fr = n - n.toInt()
                path.lineTo((a.x + (bb.x - a.x) * fr) * s, (a.y + (bb.y - a.y) * fr) * s)
            }
            ds.drawPath(path, c.of(se.tone), style = Stroke(width = (if (se.dashed) 2f else 3f) * s, cap = StrokeCap.Round,
                pathEffect = if (se.dashed) PathEffect.dashPathEffect(floatArrayOf(6f * s, 5f * s)) else null))
            se.label?.let {
                val ly = t + 6f + i * 18f
                line(Offset(l + 14f, ly), Offset(l + 36f, ly), c.of(se.tone), if (se.dashed) 2f else 3f, se.dashed)
                text(it, l + 42f, ly, if (se.tone == Tone.MUTED) c.muted else c.of(se.tone), 11f, alpha = part(0.1f, 0.4f))
            }
        }
        val a = part(0.7f, 1f)
        d.marks.forEach { m ->
            val q = map(Offset(m.x, m.y))
            ds.drawCircle(c.of(m.tone).copy(alpha = a), 4.5f * s, o(q.x, q.y))
            text(m.label, if (m.left) q.x - 8f else q.x + 8f, q.y, c.of(m.tone), 11f, if (m.left) TextAlign.End else TextAlign.Start, alpha = a)
        }
    }

    // ---- bars ------------------------------------------------------------------------------

    fun bars(d: Diagram.Bars) {
        val n = d.bars.size
        val l = 24f; val r = 300f; val b = d.height - 34f; val top = 22f
        val slot = (r - l) / n
        val w = min(56f, slot * 0.62f)
        line(Offset(l - 6f, b), Offset(r + 6f, b), c.line, 1.2f)
        d.bars.forEachIndexed { i, bar ->
            val f = part(i * 0.08f, 0.6f + i * 0.08f)
            val cx = l + slot * (i + 0.5f)
            val h = (b - top) * bar.value * f
            box(cx - w / 2f, b - h, w, h, c.of(bar.tone).copy(alpha = 0.85f), r = 5f)
            text(bar.label, cx, b + 14f, c.muted, 10.5f, TextAlign.Center, width = slot)
            text(bar.text ?: "", cx, b - h - 10f, c.of(bar.tone), 11.5f, TextAlign.Center, alpha = part(0.6f, 0.9f), bold = true, width = slot)
        }
    }

    // ---- grid ------------------------------------------------------------------------------

    fun grid(d: Diagram.Grid) {
        val rows = d.rows.size; val cols = d.cols.size
        val l = 42f; val t = 30f; val gap = 6f
        val cw = (312f - l - gap * (cols - 1)) / cols
        val ch = (d.height - t - 6f - gap * (rows - 1)) / rows
        d.cols.forEachIndexed { j, h -> text(h, l + j * (cw + gap) + cw / 2f, 14f, c.muted, 11f, TextAlign.Center, width = cw) }
        d.rows.forEachIndexed { i, h ->
            val cy = t + i * (ch + gap) + ch / 2f
            ds.drawContext.canvas.save()
            ds.drawContext.transform.rotate(-90f, o(18f, cy))
            text(h, 18f, cy, c.muted, 11f, TextAlign.Center, width = ch)
            ds.drawContext.canvas.restore()
        }
        d.cells.forEachIndexed { i, row ->
            row.forEachIndexed { j, cell ->
                val a = part(0.12f * (i * cols + j), 0.35f + 0.12f * (i * cols + j))
                val x = l + j * (cw + gap); val y = t + i * (ch + gap)
                val tone = cell.tone?.let { c.of(it) }
                box(x, y, cw, ch, tone?.copy(alpha = 0.14f) ?: c.soft, tone, alpha = a)
                val lines = cell.lines + listOfNotNull(cell.note)
                val start = y + ch / 2f - (lines.size - 1) * 8.5f
                lines.forEachIndexed { k, s1 ->
                    val isNote = cell.note != null && k == lines.size - 1
                    text(s1, x + cw / 2f, start + k * 17f, if (isNote) (tone ?: c.muted) else c.ink, if (isNote) 10.5f else 12f, TextAlign.Center, alpha = a, width = cw - 6f)
                }
            }
        }
    }

    // ---- scale -----------------------------------------------------------------------------

    fun scale(d: Diagram.Scale) {
        val y = 70f
        val f = part(0f, 0.5f)
        line(Offset(160f - 130f * f, y), Offset(160f + 130f * f, y), c.ink, 3f)
        // the fulcrum
        val tri = Path().apply { moveTo(160f * s, (y + 4f) * s); lineTo(146f * s, (y + 30f) * s); lineTo(174f * s, (y + 30f) * s); close() }
        ds.drawPath(tri, c.accent.copy(alpha = part(0.2f, 0.5f)))
        val a = part(0.4f, 0.9f)
        listOf(30f to d.left, 290f to d.right).forEach { (x, t) ->
            ds.drawCircle(c.bad.copy(alpha = a), 6f * s, o(x, y))
            text(t, x, y - 22f, c.bad, 12f, TextAlign.Center, alpha = a, width = 110f)
            text("too far", x, y + 20f, c.muted, 10.5f, TextAlign.Center, alpha = a, width = 110f)
        }
        ds.drawCircle(c.good.copy(alpha = a), 8f * s, o(160f, y))
        text(d.middle, 160f, y - 26f, c.good, 13f, TextAlign.Center, alpha = a, bold = true, width = 130f)
        text("the mean", 160f, y + 46f, c.good, 10.5f, TextAlign.Center, alpha = a)
    }

    // ---- flow ------------------------------------------------------------------------------

    fun flow(d: Diagram.Flow) {
        val n = d.steps.size
        if (d.cycle) {
            val cx = 160f; val cy = d.height / 2f; val rr = min(98f, d.height / 2f - 26f)
            val pos = List(n) { i -> val a = -PI / 2 + 2 * PI * i / n; Offset(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat()) }
            pos.forEachIndexed { i, q ->
                val a = part(i * 0.7f / n, i * 0.7f / n + 0.25f)
                val next = pos[(i + 1) % n]
                val dx = next.x - q.x; val dy = next.y - q.y; val l = sqrt(dx * dx + dy * dy)
                val from = Offset(q.x + dx / l * 30f, q.y + dy / l * 18f)
                val to = Offset(next.x - dx / l * 30f, next.y - dy / l * 18f)
                arrow(from, to, c.line, part(i * 0.7f / n + 0.15f, i * 0.7f / n + 0.35f))
                val tone = if (d.highlight == i) c.good else c.accent
                box(q.x - 50f, q.y - 15f, 100f, 30f, tone.copy(alpha = 0.14f), tone, r = 15f, alpha = a)
                text(d.steps[i], q.x, q.y, c.ink, 11f, TextAlign.Center, alpha = a, width = 96f)
            }
            return
        }
        val h = 40f; val gap = 12f
        d.steps.forEachIndexed { i, t ->
            val y = 12f + i * (h + gap)
            val a = part(i * 0.75f / n, i * 0.75f / n + 0.25f)
            val tone = if (d.highlight == i) c.good else c.accent
            box(40f, y, 240f, h, tone.copy(alpha = 0.12f), tone.copy(alpha = 0.6f), alpha = a)
            text(t, 160f, y + h / 2f, c.ink, 12f, TextAlign.Center, alpha = a, width = 228f)
            if (i < n - 1) arrow(Offset(160f, y + h + 1f), Offset(160f, y + h + gap - 1f), c.muted, part(i * 0.75f / n + 0.2f, i * 0.75f / n + 0.3f))
        }
    }

    // ---- net -------------------------------------------------------------------------------

    fun net(d: Diagram.Net) {
        d.links.forEachIndexed { i, (a, b, dashed) ->
            val pa = d.nodes[a].second; val pb = d.nodes[b].second
            val dx = pb.x - pa.x; val dy = pb.y - pa.y; val l = sqrt(dx * dx + dy * dy)
            val from = Offset(pa.x + dx / l * 34f, pa.y + dy / l * 18f)
            val to = Offset(pb.x - dx / l * 34f, pb.y - dy / l * 18f)
            if (dashed) line(from, Offset(from.x + (to.x - from.x) * part(0.5f, 0.9f), from.y + (to.y - from.y) * part(0.5f, 0.9f)), c.bad, 2f, true)
            else arrow(from, to, c.muted, part(0.2f + i * 0.08f, 0.55f + i * 0.08f))
        }
        d.nodes.forEachIndexed { i, (t, q) ->
            val a = part(i * 0.08f, 0.3f + i * 0.08f)
            val tone = d.tones[i]?.let { c.of(it) } ?: c.accent
            box(q.x - 46f, q.y - 16f, 92f, 32f, tone.copy(alpha = 0.13f), tone, r = 16f, alpha = a)
            text(t, q.x, q.y, c.ink, 11f, TextAlign.Center, alpha = a, width = 88f)
        }
    }

    // ---- timeline --------------------------------------------------------------------------

    fun timeline(d: Diagram.Timeline) {
        val l = 16f; val r = 304f; val y = 60f
        val f = part(0f, 0.6f)
        var x = l
        val colors = listOf(c.soft, c.accent.copy(alpha = 0.35f), c.accent.copy(alpha = 0.6f), c.good)
        d.parts.forEachIndexed { i, (label, frac) ->
            val w = (r - l) * frac * f
            box(x, y - 12f, max(w, 0f), 24f, colors[i % colors.size], r = 3f)
            if (frac > 0.12f) text(label, x + (r - l) * frac / 2f, y + 26f, c.muted, 10.5f, TextAlign.Center, alpha = part(0.4f, 0.7f), width = (r - l) * frac)
            x += (r - l) * frac
        }
        text(d.total, l, y - 28f, c.muted, 11f, alpha = part(0f, 0.3f))
        val a = part(0.65f, 1f)
        ds.drawRect(c.bad.copy(alpha = a), o(r - 3f, y - 16f), Size(3f * s, 32f * s))
        arrow(Offset(r - 40f, y + 52f), Offset(r - 4f, y + 18f), c.bad.copy(alpha = a), a)
        text(d.sliceLabel, r - 44f, y + 60f, c.bad, 11.5f, TextAlign.End, alpha = a, width = 220f)
    }

    // ---- pyramid ---------------------------------------------------------------------------

    fun pyramid(d: Diagram.Pyramid) {
        val n = d.levels.size
        val bottom = d.height - 10f; val top = 12f
        val lh = (bottom - top) / n
        d.levels.forEachIndexed { i, t ->
            val a = part(i * 0.15f, i * 0.15f + 0.3f)
            val y1 = bottom - i * lh; val y0 = y1 - lh + 3f
            val w1 = 280f * (1f - i.toFloat() / n); val w0 = 280f * (1f - (i + 1f) / n)
            val path = Path().apply {
                moveTo((160f - w1 / 2f) * s, y1 * s); lineTo((160f + w1 / 2f) * s, y1 * s)
                lineTo((160f + w0 / 2f) * s, y0 * s); lineTo((160f - w0 / 2f) * s, y0 * s); close()
            }
            ds.drawPath(path, c.accent.copy(alpha = (0.18f + 0.12f * i) * a))
            text(t, 160f, (y0 + y1) / 2f, c.ink, 11f, TextAlign.Center, alpha = a, width = max(w1 - 20f, 90f))
        }
    }

    // ---- rings -----------------------------------------------------------------------------

    fun rings(d: Diagram.Rings) {
        val cx = 160f; val cy = d.height / 2f
        val n = d.rings.size
        d.rings.forEachIndexed { i, t ->
            val a = part(i * 0.15f, i * 0.15f + 0.35f)
            val rr = 26f + (i + 1) * (d.height / 2f - 30f) / n
            ds.drawCircle(c.accent.copy(alpha = 0.08f * a), rr * s, o(cx, cy))
            ds.drawCircle(c.accent.copy(alpha = 0.6f * a), rr * s, o(cx, cy), style = Stroke(1.5f * s))
            text(t, cx + rr * 0.72f + 4f, cy - rr * 0.72f, c.ink, 11f, alpha = a)
        }
        ds.drawCircle(c.good.copy(alpha = part(0f, 0.2f)), 22f * s, o(cx, cy))
        text(d.centre, cx, cy, Color.White, 11f, TextAlign.Center, alpha = part(0f, 0.2f), bold = true, width = 44f)
    }

    // ---- dots ------------------------------------------------------------------------------

    fun dots(d: Diagram.Dots) {
        val cols = 25
        val rowsN = (d.total + cols - 1) / cols
        val gap = 11.4f
        val x0 = 160f - (cols - 1) * gap / 2f; val y0 = 16f
        val marks = d.marked.toMap()
        for (i in 0 until d.total) {
            val a = part(i.toFloat() / d.total * 0.5f, i.toFloat() / d.total * 0.5f + 0.2f)
            val col = i % cols; val row = i / cols
            val tone = marks[i]
            ds.drawCircle((tone?.let { c.of(it) } ?: c.line).copy(alpha = a), (if (tone != null) 4.2f else 3f) * s, o(x0 + col * gap, y0 + row * gap))
        }
        val ly = y0 + rowsN * gap + 10f
        d.legend.forEachIndexed { i, (t, tone) ->
            val a = part(0.6f, 0.9f)
            ds.drawCircle(c.of(tone).copy(alpha = a), 4.5f * s, o(30f, ly + i * 17f))
            text(t, 40f, ly + i * 17f, c.ink, 11f, alpha = a)
        }
    }

    // ---- pictures --------------------------------------------------------------------------

    fun picture(d: Diagram.Picture) {
        when (d.kind) {
            "plane" -> plane()
            "doors" -> doors()
            "cave" -> cave()
            "fibonacci" -> fibonacci()
            "helix" -> helix()
            "kintsugi" -> kintsugi()
            "trolley" -> trolley()
            "asch" -> asch()
            "orbits" -> orbits()
            "blackhole" -> blackhole()
            "flowchannel" -> flowChannel()
            "arrows" -> arrows()
            "web" -> web()
            "bell" -> bell()
            "entropy" -> entropy()
            "ship" -> ship()
            "pythagoras" -> pythagoras()
            "pi" -> piPicture()
            "pigeonhole" -> pigeonhole()
            "chunks" -> chunks()
            "gorilla" -> gorilla()
            "fractaltree" -> fractalTree()
            "eratosthenes" -> eratosthenes()
            "perspective" -> perspective()
            "plates" -> plates()
            "spacetime" -> spacetime()
            "kepler" -> kepler()
            "doubleslit" -> doubleSlit()
            "goldilocks" -> goldilocks()
            "iceberg" -> iceberg()
        }
    }

    private val sun = Color(0xFFF2B33D)
    private val sunText = Color(0xFFC9962F)
    private val sky = Color(0xFF5B7FB0)

    fun pythagoras() {
        val u = 18f
        val a = Offset(110f, 150f); val b = Offset(110f + 4 * u, 150f); val cc = Offset(110f, 150f - 3 * u)
        val f = part(0f, 0.3f)
        poly(listOf(a, b, cc, a), f, c.ink, 2.4f)
        val s3 = part(0.25f, 0.5f); val s4 = part(0.4f, 0.65f); val s5 = part(0.6f, 0.85f)
        box(a.x - 3 * u, a.y - 3 * u, 3 * u, 3 * u, c.accent.copy(alpha = 0.13f), c.accent, r = 2f, alpha = s3)
        text("9", a.x - 1.5f * u, a.y - 1.5f * u, c.accent, 14f, TextAlign.Center, alpha = s3, bold = true)
        box(a.x, a.y, 4 * u, 4 * u, c.accent.copy(alpha = 0.13f), c.accent, r = 2f, alpha = s4)
        text("16", a.x + 2 * u, a.y + 2 * u, c.accent, 14f, TextAlign.Center, alpha = s4, bold = true)
        val dx = b.x - cc.x; val dy = b.y - cc.y
        val q = listOf(cc, b, Offset(b.x + dy, b.y - dx), Offset(cc.x + dy, cc.y - dx))
        if (s5 > 0f) {
            val path = Path().apply { moveTo(q[0].x * s, q[0].y * s); q.drop(1).forEach { lineTo(it.x * s, it.y * s) }; close() }
            ds.drawPath(path, c.good.copy(alpha = 0.13f * s5))
            ds.drawPath(path, c.good.copy(alpha = s5), style = Stroke(2f * s))
        }
        text("25", q.sumOf { it.x.toDouble() }.toFloat() / 4f, q.sumOf { it.y.toDouble() }.toFloat() / 4f, c.good, 14f, TextAlign.Center, alpha = s5, bold = true)
        text("3", a.x - 8f, a.y - 1.5f * u, c.muted, 11f, TextAlign.End, alpha = f)
        text("4", a.x + 2 * u, a.y - 8f, c.muted, 11f, TextAlign.Center, alpha = f)
        text("9 + 16 = 25", 250f, 210f, c.ink, 12f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun piPicture() {
        val r = 24f; val cx = 60f; val cy = 80f; val d = 2 * r; val x0 = 110f
        val a = part(0f, 0.3f)
        ds.drawCircle(c.accent.copy(alpha = a), r * s, o(cx, cy), style = Stroke(2.5f * s))
        line(Offset(cx - r, cy), Offset(cx + r, cy), c.good.copy(alpha = a), 2.5f)
        text("1 across", cx, cy + 38f, c.good, 11f, TextAlign.Center, alpha = a)
        val f = part(0.3f, 0.75f)
        val len = PI.toFloat() * d
        line(Offset(x0, cy + r), Offset(x0 + len * f, cy + r), c.accent, 3f)
        for (k in 0 until 3) {
            val g = part(0.45f + k * 0.1f, 0.55f + k * 0.1f)
            line(Offset(x0 + k * d + 2f, cy + r - 12f), Offset(x0 + k * d + 2f + (d - 4f) * g, cy + r - 12f), c.good, 2.5f)
            text("1", x0 + k * d + d / 2f, cy + r - 24f, c.good, 11f, TextAlign.Center, alpha = g)
        }
        val g = part(0.75f, 0.85f)
        line(Offset(x0 + 3 * d + 2f, cy + r - 12f), Offset(x0 + 3 * d + 2f + (len - 3 * d - 2f) * g, cy + r - 12f), c.good, 2.5f)
        text(".14…", x0 + 3 * d + 4f, cy + r - 24f, c.good, 11f, alpha = g)
        text("round the edge = 3.14159… times across", 160f, 150f, c.accent, 11.5f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun pigeonhole() {
        text("5 pigeons, 4 holes", 160f, 40f, c.ink, 12f, TextAlign.Center, alpha = part(0f, 0.2f))
        for (i in 0 until 4) box(30f + i * 70f, 70f, 60f, 70f, c.soft, c.accent, r = 6f, alpha = part(i * 0.08f, 0.25f + i * 0.08f))
        val pos = listOf(60f to 105f, 130f to 105f, 200f to 105f, 258f to 95f, 280f to 118f)
        pos.forEachIndexed { i, (x, y) ->
            val a = part(0.35f + i * 0.1f, 0.45f + i * 0.1f)
            ds.drawCircle((if (i >= 3) c.bad else c.accent).copy(alpha = a), 8f * s, o(x, y))
        }
        text("at least one hole has two", 270f, 165f, c.bad, 11.5f, TextAlign.End, alpha = part(0.85f, 1f))
    }

    fun chunks() {
        val a = part(0f, 0.3f)
        text("0 7 7 0 0 9 0 0 1 2 3", 160f, 50f, c.muted, 20f, TextAlign.Center, alpha = a)
        text("11 separate digits: hard to hold", 160f, 78f, c.bad, 11.5f, TextAlign.Center, alpha = a)
        listOf(Triple("077", 40f, 70f), Triple("009", 125f, 70f), Triple("00123", 210f, 96f)).forEachIndexed { i, (t, x, w) ->
            val g = part(0.35f + i * 0.15f, 0.55f + i * 0.15f)
            box(x, 110f, w, 40f, c.good.copy(alpha = 0.13f), c.good, alpha = g)
            text(t, x + w / 2f, 130f, c.ink, 20f, TextAlign.Center, alpha = g)
        }
        text("3 chunks: easy", 160f, 170f, c.good, 11.5f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun gorilla() {
        val a = part(0f, 0.3f)
        text("count the passes…", 20f, 20f, c.muted, 11.5f, alpha = a)
        val players = listOf(Triple(60f, 70f, c.accent), Triple(110f, 140f, c.accent), Triple(250f, 80f, c.accent), Triple(200f, 150f, c.accent), Triple(90f, 110f, c.ink), Triple(240f, 130f, c.ink))
        players.forEach { (x, y, col) ->
            ds.drawCircle(col.copy(alpha = a), 7f * s, o(x, y - 12f)); line(Offset(x, y - 5f), Offset(x, y + 15f), col.copy(alpha = a), 3f)
        }
        val passes = listOf(Offset(60f, 62f) to Offset(110f, 128f), Offset(110f, 128f) to Offset(250f, 68f), Offset(250f, 68f) to Offset(200f, 138f))
        passes.forEachIndexed { i, (p0, p1) ->
            val f = part(0.2f + i * 0.12f, 0.35f + i * 0.12f)
            if (f > 0f) line(p0, Offset(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f), c.line, 1.5f, true)
        }
        val g = part(0.6f, 0.85f)
        val dark = Color(0xFF3A3330).copy(alpha = g)
        ds.drawCircle(dark, 13f * s, o(165f, 92f))
        box(150f, 104f, 30f, 32f, dark, r = 10f)
        line(Offset(152f, 110f), Offset(138f, 124f), dark, 6f); line(Offset(178f, 110f), Offset(192f, 124f), dark, 6f)
        text("about half never saw the gorilla", 160f, 180f, c.bad, 11.5f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun fractalTree() {
        text("each branch a smaller copy of the whole", 160f, 14f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.8f, 1f))
        fun branch(x: Float, y: Float, a: Float, l: Float, depth: Int) {
            if (depth == 0) return
            val level = 8 - depth
            val f = part(level * 0.09f, level * 0.09f + 0.12f)
            if (f <= 0f) return
            val x2 = x + l * cos(a) * f; val y2 = y - l * sin(a) * f
            line(Offset(x, y), Offset(x2, y2), if (depth < 3) c.good else c.accent, max(1f, depth * 0.7f))
            if (f >= 1f) { branch(x2, y2, a + 0.45f, l * 0.72f, depth - 1); branch(x2, y2, a - 0.45f, l * 0.72f, depth - 1) }
        }
        branch(160f, 195f, (PI / 2).toFloat(), 52f, 8)
    }

    fun eratosthenes() {
        val cx = 80f; val cy = 140f; val r0 = 70f
        val a = part(0f, 0.3f)
        ds.drawCircle(c.soft.copy(alpha = a), r0 * s, o(cx, cy))
        ds.drawCircle(c.ink.copy(alpha = a), r0 * s, o(cx, cy), style = Stroke(2f * s))
        val rays = part(0.15f, 0.4f)
        listOf(40f, 80f, 120f, 160f).forEach { x -> line(Offset(x, 10f), Offset(x, 10f + 30f * rays), sun, 2f) }
        text("the Sun's rays arrive parallel", 172f, 20f, sunText, 11f, alpha = rays)
        val a2 = (PI / 2 + 25 * PI / 180).toFloat()
        val s1 = Offset(cx, cy - r0); val s2 = Offset(cx + r0 * cos(a2), cy - r0 * sin(a2))
        val st = part(0.35f, 0.6f)
        line(s1, Offset(s1.x, s1.y - 16f), c.ink.copy(alpha = st), 3f)
        val tip = Offset(s2.x + 16f * cos(a2), s2.y - 16f * sin(a2))
        line(s2, tip, c.ink.copy(alpha = st), 3f)
        line(tip, Offset(tip.x, tip.y + 14f), sun.copy(alpha = st), 1.5f, true)
        val an = part(0.55f, 0.8f)
        line(Offset(cx, cy), s1, c.muted.copy(alpha = an), 1.2f, true); line(Offset(cx, cy), s2, c.muted.copy(alpha = an), 1.2f, true)
        text("7.2°", cx - 8f, cy - 36f, c.bad, 11f, TextAlign.End, alpha = an)
        text("Syene: no shadow", 172f, 80f, c.ink, 11f, alpha = an)
        text("Alexandria: a shadow", 172f, 100f, c.ink, 11f, alpha = an)
        val g = part(0.8f, 1f)
        text("7.2° is 1/50 of a circle,", 172f, 150f, c.good, 11f, alpha = g)
        text("so the Earth is 50 times", 172f, 166f, c.good, 11f, alpha = g)
        text("the distance between", 172f, 182f, c.good, 11f, alpha = g)
    }

    fun perspective() {
        val vx = 160f; val vy = 70f
        val a = part(0f, 0.25f)
        line(Offset(10f, vy), Offset(310f, vy), c.muted.copy(alpha = a), 1.2f)
        text("horizon", 14f, vy - 10f, c.muted, 10.5f, alpha = a)
        val f = part(0.15f, 0.55f)
        line(Offset(40f, 200f), Offset(40f + (vx - 40f) * f, 200f + (vy - 200f) * f), c.ink, 2.4f)
        line(Offset(280f, 200f), Offset(280f + (vx - 280f) * f, 200f + (vy - 200f) * f), c.ink, 2.4f)
        for (k in 1..5) {
            val t = (k / 6f).pow(1.6f)
            val g = part(0.45f + (5 - k) * 0.06f, 0.6f + (5 - k) * 0.06f)
            val y = vy + (200f - vy) * t
            line(Offset(vx - (vx - 40f) * t, y), Offset(vx + (280f - vx) * t, y), c.line.copy(alpha = g), 1.5f)
            val xr = vx + (280f - vx) * t + 8f * k / 6f
            line(Offset(xr, y), Offset(xr, y - 40f * t), c.accent.copy(alpha = g), 2.5f)
        }
        val e = part(0.8f, 1f)
        ds.drawCircle(c.bad.copy(alpha = e), 4f * s, o(vx, vy))
        text("vanishing point", vx + 8f, vy - 12f, c.bad, 11f, alpha = e)
    }

    fun plates() {
        val a = part(0f, 0.3f)
        box(20f, 150f, 140f, 40f, c.accent.copy(alpha = 0.27f), c.accent, r = 3f, alpha = a)
        box(180f, 150f, 120f, 40f, c.good.copy(alpha = 0.27f), c.good, r = 3f, alpha = a)
        text("India", 80f, 205f, c.accent, 11f, TextAlign.Center, alpha = a)
        text("Asia", 240f, 205f, c.good, 11f, TextAlign.Center, alpha = a)
        val p = part(0.25f, 0.5f)
        arrow(Offset(40f, 170f), Offset(40f + 80f * p, 170f), c.accent, if (p > 0f) 1f else 0f)
        arrow(Offset(280f, 170f), Offset(280f - 80f * p, 170f), c.good, if (p > 0f) 1f else 0f)
        text("plates push together…", 20f, 36f, c.ink, 11.5f, alpha = p)
        val m = part(0.5f, 0.85f)
        val peak = 150f - 55f * m
        val mountain = Path().apply {
            moveTo(140f * s, 150f * s); lineTo(158f * s, (150f - 30f * m) * s); lineTo(170f * s, peak * s)
            lineTo(182f * s, (150f - 30f * m) * s); lineTo(200f * s, 150f * s); close()
        }
        ds.drawPath(mountain, c.line.copy(alpha = 0.6f))
        ds.drawPath(mountain, c.ink, style = Stroke(2.4f * s))
        line(Offset(20f, 150f), Offset(140f, 150f), c.ink, 2.4f); line(Offset(200f, 150f), Offset(300f, 150f), c.ink, 2.4f)
        val e = part(0.8f, 1f)
        text("…and the land buckles up into mountains", 300f, 60f, c.ink, 11.5f, TextAlign.End, alpha = e)
        text("Himalayas", 170f, 82f, c.bad, 11f, TextAlign.Center, alpha = e)
    }

    fun spacetime() {
        val bx = 160f; val by = 110f
        val dip = part(0f, 0.7f)
        fun warp(x: Float, y: Float): Offset {
            val dx = x - bx; val dy = y - by; val f = 900f / (dx * dx + dy * dy + 900f) * dip
            return Offset(x - dx * f * 0.35f, y + 40f * f)
        }
        var gx = 20f
        while (gx <= 320f) { poly(List(37) { k -> warp(gx, 20f + k * 5f) }, 1f, c.line, 1.3f); gx += 25f }
        var gy = 20f
        while (gy <= 200f) { poly(List(57) { k -> warp(20f + k * 5f, gy) }, 1f, c.line, 1.3f); gy += 20f }
        ds.drawCircle(c.accent, 18f * s * (0.4f + 0.6f * dip), o(bx, by + 30f * dip))
        val e = part(0.75f, 1f)
        ds.drawCircle(c.good.copy(alpha = e), 5f * s, o(260f, 118f))
        text("planets follow the curve", 258f, 98f, c.good, 10.5f, TextAlign.Center, alpha = e)
        text("the Sun bends spacetime", 160f, 212f, c.ink, 11.5f, TextAlign.Center, alpha = e)
    }

    fun kepler() {
        val cx = 170f; val cy = 95f; val a = 120f; val b = 68f
        val fx = cx - sqrt(a * a - b * b)
        val ell = List(81) { k -> val t = k / 80f * 2f * PI.toFloat(); Offset(cx + a * cos(t), cy + b * sin(t)) }
        poly(ell, part(0f, 0.4f), c.ink, 2f)
        ds.drawCircle(sun.copy(alpha = part(0.1f, 0.3f)), 10f * s, o(fx, cy))
        fun wedge(t0: Float, t1: Float, al: Float) {
            if (al <= 0f) return
            val path = Path().apply {
                moveTo(fx * s, cy * s)
                for (k in 0..10) { val t = t0 + (t1 - t0) * k / 10f; lineTo((cx + a * cos(t)) * s, (cy + b * sin(t)) * s) }
                close()
            }
            ds.drawPath(path, c.good.copy(alpha = 0.2f * al)); ds.drawPath(path, c.good.copy(alpha = al), style = Stroke(1.5f * s))
        }
        val r = (PI / 180).toFloat()
        val w1 = part(0.45f, 0.7f); val w2 = part(0.6f, 0.85f)
        wedge(150f * r, 210f * r, w1); wedge(-14f * r, 14f * r, w2)
        text("near the Sun: fast", 60f, 185f, c.good, 10.5f, TextAlign.Center, alpha = w1)
        text("far away: slow", 280f, 185f, c.good, 10.5f, TextAlign.Center, alpha = w2)
        text("equal times sweep equal areas", 170f, 205f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun doubleSlit() {
        val a = part(0f, 0.25f)
        ds.drawCircle(c.accent.copy(alpha = a), 8f * s, o(30f, 105f))
        listOf(20f to 85f, 95f to 115f, 125f to 190f).forEach { (y0, y1) -> line(Offset(110f, y0), Offset(110f, y1), c.ink.copy(alpha = a), 4f) }
        text("two slits", 60f, 205f, c.ink, 11f, TextAlign.Center, alpha = a)
        for (k in 1..5) {
            val g = part(0.2f + k * 0.08f, 0.35f + k * 0.08f)
            listOf(90f, 120f).forEach { sy ->
                val pts = List(21) { i -> val t = -1.1f + 2.2f * i / 20f; Offset(110f + k * 30f * cos(t), sy + k * 30f * sin(t)) }.filter { it.x < 282f && it.y > 15f && it.y < 195f }
                if (pts.size > 1) poly(pts, 1f, c.line.copy(alpha = g), 1.2f)
            }
        }
        val e = part(0.75f, 1f)
        line(Offset(285f, 20f), Offset(285f, 190f), c.muted, 1f)
        for (k in -4..4 step 2) box(285f, 105f + k * 18f - 6f, 18f, 12f, c.good, r = 2f, alpha = e)
        text("bands of light and dark", 300f, 212f, c.good, 11f, TextAlign.End, alpha = e)
    }

    fun goldilocks() {
        val sx = 40f; val sy = 110f
        ds.drawCircle(sun.copy(alpha = part(0f, 0.2f)), 22f * s, o(sx, sy))
        listOf(Triple(80f, c.bad, 22f), Triple(130f, c.good, 34f), Triple(200f, sky, 40f)).forEachIndexed { i, (r, col, w) ->
            val g = part(0.15f + i * 0.15f, 0.4f + i * 0.15f)
            ds.drawArc(col.copy(alpha = 0.33f * g), -40f, 80f * g, false, o(sx - r, sy - r), Size(2 * r * s, 2 * r * s), style = Stroke(w * s))
            ds.drawCircle(col.copy(alpha = g), (if (i == 1) 7f else 6f) * s, o(sx + r, sy))
        }
        val e = part(0.7f, 1f)
        text("too hot", sx + 80f, sy + 70f, c.bad, 11f, TextAlign.Center, alpha = e)
        text("just right", sx + 130f, sy + 92f, c.good, 11.5f, TextAlign.Center, alpha = e, bold = true)
        text("too cold", sx + 200f, sy + 118f, sky, 11f, TextAlign.Center, alpha = e)
        text("liquid water possible", sx + 130f, sy - 92f, c.good, 11f, TextAlign.Center, alpha = e)
    }

    fun iceberg() {
        val wy = 60f
        val a = part(0f, 0.25f)
        ds.drawRect(sky.copy(alpha = 0.2f * a), o(0f, wy), Size(320f * s, 170f * s))
        line(Offset(0f, wy), Offset(320f, wy), sky.copy(alpha = a), 2f)
        val top = listOf(Offset(140f, wy), Offset(150f, 30f), Offset(165f, 20f), Offset(180f, 32f), Offset(190f, wy))
        poly(top, part(0.2f, 0.45f), c.ink, 2f)
        text("what the story shows", 200f, 24f, c.ink, 11.5f, alpha = part(0.4f, 0.6f))
        val below = listOf(Offset(190f, wy), Offset(225f, 100f), Offset(240f, 160f), Offset(200f, 215f), Offset(130f, 215f), Offset(90f, 170f), Offset(100f, 110f), Offset(140f, wy))
        poly(below, part(0.5f, 0.85f), c.ink, 2f)
        text("what the writer knows and leaves unsaid", 165f, 140f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.8f, 1f), width = 150f)
    }

    fun poly(pts: List<Offset>, f: Float, color: Color, w: Float = 2.2f, close: Boolean = false, fill: Color? = null) {
        if (pts.size < 2) return
        val path = Path(); path.moveTo(pts[0].x * s, pts[0].y * s)
        val n = (pts.size - 1) * f
        var k = 1
        while (k <= n.toInt()) { path.lineTo(pts[k].x * s, pts[k].y * s); k++ }
        if (k < pts.size && n > n.toInt()) {
            val a = pts[k - 1]; val b = pts[k]; val fr = n - n.toInt()
            path.lineTo((a.x + (b.x - a.x) * fr) * s, (a.y + (b.y - a.y) * fr) * s)
        }
        if (close && f >= 1f) { path.close(); fill?.let { ds.drawPath(path, it) } }
        ds.drawPath(path, color, style = Stroke(w * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    fun plane() {
        val outline = listOf(160f to 18f, 170f to 40f, 172f to 92f, 292f to 118f, 292f to 132f, 172f to 124f, 170f to 162f, 200f to 178f, 200f to 188f,
            160f to 182f, 120f to 188f, 120f to 178f, 150f to 162f, 148f to 124f, 28f to 132f, 28f to 118f, 148f to 92f, 150f to 40f, 160f to 18f).map { Offset(it.first, it.second) }
        poly(outline, part(0f, 0.5f), c.ink, close = true, fill = c.soft)
        val a = part(0.45f, 0.75f)
        listOf(90f to 117f, 110f to 112f, 70f to 122f, 240f to 113f, 262f to 119f, 220f to 118f, 102f to 124f, 250f to 125f, 160f to 150f, 154f to 140f,
            166f to 172f, 182f to 182f, 140f to 182f).forEach { (x, y) -> ds.drawCircle(c.bad.copy(alpha = a), 3.2f * s, o(x, y)) }
        val g = part(0.75f, 1f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5f * s, 4f * s))
        ds.drawCircle(c.good.copy(alpha = g), 22f * s, o(160f, 62f), style = Stroke(2.4f * s, pathEffect = dash))
        ds.drawCircle(c.good.copy(alpha = g), 14f * s, o(160f, 108f), style = Stroke(2.4f * s, pathEffect = dash))
        text("no holes here:", 190f, 56f, c.good, 11.5f, alpha = g)
        text("engines, cockpit", 190f, 70f, c.good, 11.5f, alpha = g)
        text("holes on planes", 14f, 158f, c.bad, 11f, alpha = a)
        text("that came back", 14f, 172f, c.bad, 11f, alpha = a)
    }

    fun doors() {
        val xs = listOf(30f, 125f, 220f)
        xs.forEachIndexed { i, x ->
            val a = part(i * 0.1f, 0.3f + i * 0.1f)
            box(x, 30f, 70f, 110f, c.soft, c.ink.copy(alpha = 0.6f), r = 6f, alpha = a)
            text("${i + 1}", x + 35f, 85f, c.ink, 18f, TextAlign.Center, alpha = a, bold = true)
        }
        val pick = part(0.3f, 0.5f)
        text("your pick", 65f, 18f, c.accent, 11f, TextAlign.Center, alpha = pick)
        text("1/3", 65f, 158f, c.accent, 14f, TextAlign.Center, alpha = pick, bold = true)
        val open = part(0.5f, 0.75f)
        box(125f, 30f, 70f, 110f, c.muted.copy(alpha = 0.25f), alpha = open)
        text("goat", 160f, 85f, c.muted, 12f, TextAlign.Center, alpha = open)
        text("host opens", 160f, 18f, c.muted, 11f, TextAlign.Center, alpha = open)
        val sw = part(0.75f, 1f)
        box(220f, 30f, 70f, 110f, c.good.copy(alpha = 0.15f), c.good, r = 6f, alpha = sw)
        text("2/3", 255f, 158f, c.good, 14f, TextAlign.Center, alpha = sw, bold = true)
        text("switch here", 255f, 18f, c.good, 11f, TextAlign.Center, alpha = sw)
        arrow(Offset(100f, 178f), Offset(220f, 178f), c.good, sw)
    }

    fun cave() {
        val f = part(0f, 0.4f)
        poly(listOf(Offset(10f, 190f), Offset(10f, 30f), Offset(240f, 30f), Offset(300f, 10f)), f, c.ink.copy(alpha = 0.7f), 3f)
        poly(listOf(Offset(10f, 190f), Offset(310f, 190f)), f, c.ink.copy(alpha = 0.7f), 3f)
        val a = part(0.3f, 0.6f)
        box(20f, 60f, 10f, 110f, c.muted.copy(alpha = 0.35f), alpha = a)
        text("wall", 25f, 52f, c.muted, 10.5f, TextAlign.Center, alpha = a)
        ds.drawCircle(Color(0xFFE8A13A).copy(alpha = a), 10f * s, o(250f, 120f))
        text("fire", 250f, 142f, Color(0xFFE8A13A), 10.5f, TextAlign.Center, alpha = a)
        for (k in 0 until 3) {
            val x = 120f + k * 22f
            ds.drawCircle(c.ink.copy(alpha = a), 6f * s, o(x, 150f))
            line(Offset(x, 156f), Offset(x, 182f), c.ink.copy(alpha = a), 3f)
        }
        text("prisoners, chained", 145f, 176f, c.ink, 10.5f, TextAlign.Center, alpha = a)
        line(Offset(190f, 104f), Offset(320f, 104f), c.muted.copy(alpha = a), 2f)
        text("figures carried past", 255f, 92f, c.muted, 10.5f, TextAlign.Center, alpha = a)
        val sh = part(0.6f, 0.9f)
        listOf(40f, 62f).forEach { y -> box(32f, y + 30f, 16f, 22f, c.ink.copy(alpha = 0.55f), r = 4f, alpha = sh) }
        text("shadows: all they know", 52f, 128f, c.bad, 11f, alpha = sh)
        val out = part(0.85f, 1f)
        ds.drawCircle(Color(0xFFF2C14E).copy(alpha = out), 12f * s, o(296f, 22f))
        text("the sun outside", 296f, 46f, Color(0xFFC9962F), 10.5f, TextAlign.End, alpha = out)
    }

    fun fibonacci() {
        val sizes = listOf(1, 1, 2, 3, 5, 8, 13)
        // Each square goes right, down, left or up of the ones before; a quarter circle in each makes the spiral.
        val squares = mutableListOf(Triple(0f, 0f, 1f))
        var x0 = 0f; var y0 = 0f; var x1 = 1f; var y1 = 1f
        val dirs = listOf("right", "down", "left", "up")
        for (i in 1 until sizes.size) {
            val n = sizes[i].toFloat()
            val (x, y) = when (dirs[(i - 1) % 4]) {
                "right" -> x1 to y0
                "down" -> x0 to y1
                "left" -> (x0 - n) to (y1 - n)
                else -> (x1 - n) to (y0 - n)
            }
            squares += Triple(x, y, n)
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x + n); y1 = max(y1, y + n)
        }
        val u = 8.5f
        val ox = 160f - (x0 + x1) / 2f * u
        val oy = 8f - y0 * u
        squares.forEachIndexed { i, (x, y, n) ->
            val a = part(i * 0.07f, i * 0.07f + 0.25f)
            box(ox + x * u, oy + y * u, n * u, n * u, c.accent.copy(alpha = 0.04f + 0.02f * i), c.accent.copy(alpha = 0.55f), r = 1f, alpha = a)
            if (n >= 3f) text("${n.toInt()}", ox + (x + n / 2f) * u, oy + (y + n / 2f) * u, c.accent, 9f + n, TextAlign.Center, alpha = a)
        }
        val pts = mutableListOf<Offset>()
        squares.forEachIndexed { k, (x, y, n) ->
            val d = if (k == 0) "first" else dirs[(k - 1) % 4]
            val (cx, cy) = when (d) {
                "first" -> (x + n) to (y + n)
                "right" -> x to (y + n)
                "down" -> x to y
                "left" -> (x + n) to y
                else -> (x + n) to (y + n)
            }
            val a0 = when (d) { "first" -> 180f; "right" -> 270f; "down" -> 0f; "left" -> 90f; else -> 180f }
            for (t in 0..9) {
                val ang = Math.toRadians((a0 + t * 10f).toDouble())
                pts += Offset(ox + (cx + n * cos(ang).toFloat()) * u, oy + (cy + n * sin(ang).toFloat()) * u)
            }
        }
        poly(pts, part(0.5f, 1f), c.good, 2.4f)
        text("1, 1, 2, 3, 5, 8, 13 …", 160f, 203f, c.ink, 12f, TextAlign.Center, alpha = part(0.3f, 0.6f))
    }

    fun helix() {
        val f = part(0f, 0.8f)
        val n = 60
        val a = List(n) { k -> val y = 16f + k * 3f; Offset(160f + 60f * sin(k / 6f), y) }
        val b = List(n) { k -> val y = 16f + k * 3f; Offset(160f - 60f * sin(k / 6f), y) }
        for (k in 0 until n step 4) {
            val g = part(k.toFloat() / n * 0.8f, k.toFloat() / n * 0.8f + 0.2f)
            val col = if ((k / 4) % 2 == 0) c.good else c.accent
            line(a[k], Offset(160f, a[k].y), col.copy(alpha = g), 3f); line(Offset(160f, b[k].y), b[k], c.bad.copy(alpha = 0.7f * g), 3f)
        }
        poly(a, f, c.ink, 3f); poly(b, f, c.ink.copy(alpha = 0.6f), 3f)
        text("two strands", 236f, 30f, c.muted, 11f, alpha = part(0.7f, 1f))
        text("paired letters: A–T, C–G", 236f, 120f, c.muted, 11f, alpha = part(0.7f, 1f), width = 80f)
    }

    fun kintsugi() {
        val f = part(0f, 0.45f)
        val bowl = List(40) { k -> val t = PI.toFloat() * k / 39f; Offset(160f - 110f * cos(t), 60f + 100f * sin(t)) }
        poly(bowl, f, c.ink, 3f)
        poly(listOf(Offset(50f, 60f), Offset(270f, 60f)), f, c.ink, 3f)
        val g = part(0.45f, 1f)
        val crack = listOf(Offset(120f, 60f), Offset(132f, 92f), Offset(118f, 118f), Offset(140f, 146f), Offset(132f, 158f))
        val crack2 = listOf(Offset(132f, 92f), Offset(170f, 104f), Offset(206f, 96f), Offset(230f, 120f))
        poly(crack, g, Color(0xFFD4A437), 4f); poly(crack2, g, Color(0xFFD4A437), 4f)
        text("the break, mended in gold", 160f, 186f, Color(0xFFB08524), 12f, TextAlign.Center, alpha = part(0.8f, 1f))
    }

    fun trolley() {
        val f = part(0f, 0.5f)
        poly(listOf(Offset(20f, 120f), Offset(150f, 120f)), f, c.ink, 3f)
        poly(listOf(Offset(150f, 120f), Offset(300f, 70f)), part(0.25f, 0.6f), c.ink, 3f)
        poly(listOf(Offset(150f, 120f), Offset(300f, 170f)), part(0.25f, 0.6f), c.ink, 3f)
        val a = part(0.2f, 0.4f)
        box(30f, 104f, 46f, 26f, c.accent, alpha = a)
        text("trolley", 53f, 144f, c.muted, 10.5f, TextAlign.Center, alpha = a)
        val p1 = part(0.6f, 0.85f)
        for (k in 0 until 5) ds.drawCircle(c.bad.copy(alpha = p1), 5.5f * s, o(232f + k * 13f, 166f - k * 4.2f))
        text("five on this track", 290f, 196f, c.bad, 11f, TextAlign.End, alpha = p1)
        ds.drawCircle(c.bad.copy(alpha = p1), 5.5f * s, o(262f, 80f))
        text("one on this one", 290f, 46f, c.bad, 11f, TextAlign.End, alpha = p1)
        val lv = part(0.8f, 1f)
        line(Offset(150f, 140f), Offset(140f, 166f), c.good.copy(alpha = lv), 3f)
        text("pull the lever?", 136f, 180f, c.good, 11.5f, TextAlign.End, alpha = lv, bold = true)
    }

    fun asch() {
        val a = part(0f, 0.3f)
        line(Offset(60f, 50f), Offset(60f, 150f), c.ink.copy(alpha = a), 5f)
        text("this line", 60f, 166f, c.muted, 11f, TextAlign.Center, alpha = a)
        val lens = listOf(70f, 100f, 128f)
        lens.forEachIndexed { i, l ->
            val g = part(0.25f + i * 0.1f, 0.5f + i * 0.1f)
            val x = 170f + i * 50f
            val col = if (i == 1) c.good else c.ink
            line(Offset(x, 150f - l * g), Offset(x, 150f), col, 5f)
            text("ABC"[i].toString(), x, 166f, c.muted, 12f, TextAlign.Center, alpha = g)
        }
        val e = part(0.75f, 1f)
        text("matches which?", 220f, 22f, c.ink, 12f, TextAlign.Center, alpha = e)
        text("the group says A", 220f, 186f, c.bad, 11.5f, TextAlign.Center, alpha = e)
    }

    fun orbits() {
        val cx = 160f; val cy = 105f
        val a = part(0f, 0.3f)
        ds.drawCircle(Color(0xFFF2B33D).copy(alpha = a), 16f * s, o(cx, cy))
        val planets = listOf(34f to "Mercury", 56f to "Venus", 80f to "Earth", 102f to "Mars")
        planets.forEachIndexed { i, (r, name) ->
            val g = part(0.2f + i * 0.12f, 0.45f + i * 0.12f)
            ds.drawCircle(c.line.copy(alpha = g), r * s, o(cx, cy), style = Stroke(1.2f * s))
            val ang = -0.6f - i * 0.9f + g * 0.6f
            val px = cx + r * cos(ang); val py = cy + r * sin(ang)
            ds.drawCircle((if (name == "Earth") c.good else c.accent).copy(alpha = g), (if (name == "Earth") 6f else 4.5f) * s, o(px, py))
            if (name == "Earth") text("Earth", px + 8f, py - 8f, c.good, 11f, alpha = g)
        }
        text("the Sun at the centre", cx, 200f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.8f, 1f))
    }

    fun blackhole() {
        val cx = 160f; val cy = 95f
        val f = part(0f, 0.5f)
        for (k in 0 until 6) ds.drawCircle(c.accent.copy(alpha = 0.10f * f), (60f + k * 9f) * s, o(cx, cy), style = Stroke(4f * s))
        ds.drawCircle(Color.Black.copy(alpha = f), 40f * s, o(cx, cy))
        ds.drawCircle(c.bad.copy(alpha = part(0.4f, 0.7f)), 40f * s, o(cx, cy), style = Stroke(2f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 4f * s))))
        val a = part(0.5f, 0.85f)
        arrow(Offset(20f, 40f), Offset(cx - 44f, cy - 18f), c.muted, a)
        text("light falls in", 20f, 28f, c.muted, 11f, alpha = a)
        text("event horizon:", 240f, 160f, c.bad, 11.5f, alpha = part(0.7f, 1f))
        text("nothing returns", 240f, 175f, c.bad, 11.5f, alpha = part(0.7f, 1f))
    }

    fun flowChannel() {
        val l = 40f; val r = 300f; val t = 16f; val b = 186f
        line(Offset(l, b), Offset(r, b), c.line, 1.2f); line(Offset(l, t), Offset(l, b), c.line, 1.2f)
        text("skill →", r, b + 13f, c.muted, 11f, TextAlign.End)
        ds.drawContext.canvas.save(); ds.drawContext.transform.rotate(-90f, o(20f, (t + b) / 2f))
        text("challenge →", 20f, (t + b) / 2f, c.muted, 11f, TextAlign.Center); ds.drawContext.canvas.restore()
        val f = part(0f, 0.6f)
        val band = Path().apply {
            moveTo(l * s, (b - 10f) * s); lineTo((l + (r - l) * f) * s, (b - 10f - (b - t - 30f) * f) * s)
            lineTo((l + (r - l) * f) * s, (b - 10f - (b - t - 30f) * f + 40f) * s); lineTo((l + 26f) * s, b * s); close()
        }
        ds.drawPath(band, c.good.copy(alpha = 0.22f))
        val a = part(0.6f, 0.95f)
        text("FLOW", 200f, 104f, c.good, 15f, TextAlign.Center, alpha = a, bold = true)
        text("anxiety: too hard", 70f, 40f, c.bad, 11.5f, alpha = a)
        text("boredom: too easy", 290f, 160f, c.muted, 11.5f, TextAlign.End, alpha = a)
    }

    fun arrows() {
        val a = part(0f, 0.4f)
        ds.drawCircle(c.ink.copy(alpha = a), 10f * s, o(160f, 110f))
        text("you", 160f, 132f, c.muted, 11f, TextAlign.Center, alpha = a)
        val f1 = part(0.15f, 0.45f)
        arrow(Offset(40f, 70f), Offset(146f, 104f), c.bad, f1)
        text("1st arrow: the pain", 40f, 56f, c.bad, 11.5f, alpha = f1)
        val f2 = part(0.55f, 0.9f)
        arrow(Offset(280f, 70f), Offset(174f, 104f), c.bad.copy(alpha = 0.6f), f2, dashed = true)
        text("2nd arrow: blame, worry", 290f, 56f, c.bad.copy(alpha = 0.7f), 11.5f, TextAlign.End, alpha = f2)
        text("the one we can learn not to shoot", 160f, 176f, c.good, 12f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun web() {
        val n = 9
        val pts = List(n) { i -> val ang = 2 * PI * i / n; Offset(160f + 92f * cos(ang).toFloat(), 108f + 84f * sin(ang).toFloat()) }
        var k = 0
        for (i in 0 until n) for (j in i + 1 until n) {
            if ((i + j) % 3 == 0 || j == i + 1) {
                val g = part(k * 0.02f, k * 0.02f + 0.4f); k++
                line(pts[i], Offset(pts[i].x + (pts[j].x - pts[i].x) * g, pts[i].y + (pts[j].y - pts[i].y) * g), c.accent.copy(alpha = 0.45f), 1.5f)
            }
        }
        pts.forEachIndexed { i, q ->
            val a = part(0.3f + i * 0.04f, 0.5f + i * 0.04f)
            ds.drawCircle(c.good.copy(alpha = a), 6f * s, o(q.x, q.y))
        }
        text("touch one, the others move", 160f, 210f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.85f, 1f))
    }

    fun bell() {
        val l = 30f; val r = 300f; val b = 170f
        line(Offset(l, b), Offset(r, b), c.line, 1.2f)
        val f = part(0f, 0.6f)
        val pts = List(81) { k -> val x = -3f + 6f * k / 80f; Offset(l + (r - l) * k / 80f, b - 140f * exp(-x * x / 2f)) }
        val sh = part(0.5f, 0.85f)
        val area = Path().apply {
            val i0 = 27; val i1 = 53
            moveTo(pts[i0].x * s, b * s); for (k in i0..i1) lineTo(pts[k].x * s, pts[k].y * s); lineTo(pts[i1].x * s, b * s); close()
        }
        ds.drawPath(area, c.good.copy(alpha = 0.18f * sh))
        poly(pts, f, c.accent, 3f)
        text("about 68% within one step of the average", 165f, 188f, c.good, 11f, TextAlign.Center, alpha = sh)
        text("average", 165f, 18f, c.muted, 11f, TextAlign.Center, alpha = sh)
    }

    fun entropy() {
        val r = java.util.Random(4)
        val left = List(30) { Offset(30f + r.nextFloat() * 90f, 40f + r.nextFloat() * 110f) }
        val mixed = List(30) { Offset(30f + r.nextFloat() * 260f, 40f + r.nextFloat() * 110f) }
        box(20f, 30f, 280f, 130f, c.soft, c.line, r = 8f)
        val f = part(0.2f, 0.9f)
        left.forEachIndexed { i, q ->
            val m = mixed[i]
            ds.drawCircle(c.bad.copy(alpha = 0.85f), 4f * s, o(q.x + (m.x - q.x) * f, q.y + (m.y - q.y) * f))
        }
        text("ordered", 70f, 18f, c.muted, 11f, TextAlign.Center, alpha = 1f - f)
        text("spread out: more ways to be", 160f, 178f, c.ink, 11.5f, TextAlign.Center, alpha = part(0.8f, 1f))
    }

    fun ship() {
        val planks = 8
        for (i in 0 until planks) {
            val g = part(0.1f + i * 0.09f, 0.2f + i * 0.09f)
            val x = 40f + i * 30f
            box(x, 70f, 26f, 50f, c.accent.copy(alpha = 0.35f), c.accent, r = 3f)
            box(x, 70f, 26f, 50f, c.good.copy(alpha = 0.45f), c.good, r = 3f, alpha = g)
        }
        poly(listOf(Offset(30f, 120f), Offset(60f, 150f), Offset(260f, 150f), Offset(290f, 120f)), 1f, c.ink, 2.5f)
        text("old planks", 70f, 54f, c.accent, 11f, TextAlign.Center)
        text("new planks", 250f, 54f, c.good, 11f, TextAlign.Center, alpha = part(0.4f, 0.7f))
        text("every plank replaced: the same ship?", 160f, 176f, c.ink, 12f, TextAlign.Center, alpha = part(0.9f, 1f))
    }
}

/** The diagrams, by the title of the idea or myth they illustrate. */
object Diagrams {
    private fun curve(n: Int = 40, f: (Float) -> Float) = List(n + 1) { k -> val x = k / n.toFloat(); Offset(x, f(x)) }

    private val all: Map<String, Diagram> by lazy {
        mapOf(
            "Survivorship Bias" to Diagram.Picture("plane", "Hits on the wings didn't bring planes down. Planes hit in the green areas never returned, so the armour goes there."),
            "Exponential Growth" to Diagram.Lines(
                listOf(
                    Diagram.Series(List(30) { d -> Offset(d / 29f, 2f.pow(d - 29)) }, "doubling every day", Tone.ACCENT),
                    Diagram.Series(listOf(Offset(0f, 0f), Offset(1f, 0.3f)), "steady growth", Tone.MUTED, dashed = true),
                ),
                listOf(Diagram.Mark(28f / 29f, 0.5f, "day 29: half"), Diagram.Mark(1f, 1f, "day 30: full")),
                "day 1" to "day 30",
                caption = "A lily patch that doubles daily covers half the pond only the day before it covers all of it.",
            ),
            "The Prisoner's Dilemma" to Diagram.Grid(
                listOf("B stays silent", "B confesses"), listOf("A silent", "A confesses"),
                listOf(
                    listOf(Diagram.Cell(listOf("A: 1 year", "B: 1 year"), Tone.GOOD, "best together"), Diagram.Cell(listOf("A: 10 years", "B: goes free"))),
                    listOf(Diagram.Cell(listOf("A: goes free", "B: 10 years")), Diagram.Cell(listOf("A: 5 years", "B: 5 years"), Tone.BAD, "where both end up")),
                ),
                "Whatever B does, A is better off confessing, and the same for B. So both confess, and both do worse than if they'd trusted each other.",
            ),
            "The Monty Hall Problem" to Diagram.Picture("doors", "Your first pick is right one time in three. The host knowingly opens a goat door, so the other two-thirds now sits on the door that's left."),
            "Plato's Cave" to Diagram.Picture("cave", "Chained since childhood, the prisoners see only shadows of things carried past the fire, and take them for the world."),
            "The Fibonacci Sequence" to Diagram.Picture("fibonacci", "Each number is the sum of the two before. Squares of those sizes fit together, and a curve through them makes the spiral seen in shells and seed heads.", 214f),
            "The Double Helix" to Diagram.Picture("helix", "Two strands wound round each other, joined by pairs of bases that always match: A with T, C with G.", 200f),
            "Kintsugi" to Diagram.Picture("kintsugi", "The break isn't hidden: it is traced in gold and becomes part of the bowl's story.", 200f),
            "The Trolley Problem" to Diagram.Picture("trolley", "Most would pull the lever to save five. Fewer would push a person off a bridge to do the same, though the numbers match."),
            "Conformity" to Diagram.Picture("asch", "In Asch's studies the answer was obvious (B), yet when the group all said A, about three quarters of people went along at least once.", 196f),
            "Heliocentrism" to Diagram.Picture("orbits", "Copernicus put the Sun at the centre, with the Earth one planet among others going round it.", 212f),
            "Black Holes" to Diagram.Picture("blackhole", "Inside the event horizon every path leads inward. Even light can't climb back out.", 190f),
            "Flow" to Diagram.Picture("flowchannel", "Flow lives where the challenge matches your skill: too hard brings anxiety, too easy brings boredom. As skill grows, the challenge can grow too.", 200f),
            "The Second Arrow" to Diagram.Picture("arrows", "The first arrow, pain, can't always be avoided. The second, the story we add, is ours to drop.", 190f),
            "The Web of Life" to Diagram.Picture("web", "Every living thing is tied to many others: a change in one strand runs through the whole web.", 220f),
            "Indra's Net" to Diagram.Picture("web", "In Indra's net each jewel reflects all the others: nothing exists on its own.", 220f),
            "The Bell Curve" to Diagram.Picture("bell", "Heights, errors, test scores: many things cluster round an average and thin out symmetrically on either side.", 196f),
            "Entropy" to Diagram.Picture("entropy", "Left alone, things spread out from order towards mixture, because there are far more mixed arrangements than tidy ones.", 190f),
            "The Ship of Theseus" to Diagram.Picture("ship", "Replace every plank, one by one. Is it still the same ship? Our bodies renew their cells too.", 190f),
            "The Golden Mean" to Diagram.Scale("cowardice", "courage", "recklessness", "Each virtue sits between a too-little and a too-much, found by judgement, not by rule."),
            "The Middle Way" to Diagram.Scale("indulgence", "the middle way", "harsh austerity", "The Buddha tried both extremes and found the path between them."),
            "The Hedonic Treadmill" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> 0.3f + if (x < 0.25f) 0f else 0.55f * exp(-(x - 0.25f) * 6f) }, "how happy you feel", Tone.ACCENT),
                    Diagram.Series(listOf(Offset(0f, 0.3f), Offset(1f, 0.3f)), "your usual level", Tone.MUTED, dashed = true),
                ),
                listOf(Diagram.Mark(0.25f, 0.85f, "pay rise", left = false), Diagram.Mark(0.95f, 0.31f, "back to usual")),
                "before" to "a year later",
                caption = "We quickly get used to good things, and our happiness drifts back towards where it was.",
            ),
            "The Peak–End Rule" to Diagram.Lines(
                listOf(Diagram.Series(curve { x -> 0.25f + 0.65f * exp(-((x - 0.35f) * 7f).pow(2)) + if (x > 0.75f) -0.15f * (x - 0.75f) * 4f else 0f }, "how bad it felt", Tone.BAD)),
                listOf(Diagram.Mark(0.35f, 0.9f, "the peak", left = false), Diagram.Mark(1f, 0.1f, "the end")),
                "start" to "finish",
                caption = "We remember an experience by its worst (or best) moment and how it ended, hardly by how long it lasted.",
            ),
            "Loss Aversion" to Diagram.Bars(
                listOf(Diagram.Bar("lose £100", 0.9f, Tone.BAD, "hurts this much"), Diagram.Bar("win £100", 0.45f, Tone.GOOD, "pleases this much")),
                caption = "A loss feels about twice as strong as a gain of the same size.",
            ),
            "The Pareto Principle" to Diagram.Bars(
                listOf(Diagram.Bar("20% of the causes", 0.8f, Tone.ACCENT, "80%"), Diagram.Bar("the other 80%", 0.2f, Tone.MUTED, "20%")),
                caption = "Often a few causes produce most of the effect: roughly 80% from 20%, though rarely exactly.",
            ),
            "The Spotlight Effect" to Diagram.Bars(
                listOf(Diagram.Bar("wearers guessed", 0.5f, Tone.MUTED, "about half"), Diagram.Bar("actually noticed", 0.23f, Tone.GOOD, "about a quarter")),
                caption = "In the embarrassing T-shirt study, far fewer people noticed than the wearers feared.",
            ),
            "The Paradox of Choice" to Diagram.Bars(
                listOf(Diagram.Bar("6 jams", 0.3f, Tone.GOOD, "30% bought"), Diagram.Bar("24 jams", 0.03f, Tone.BAD, "3% bought")),
                caption = "More shoppers stopped at the big display, but far fewer of them bought anything.",
            ),
            "Anchoring" to Diagram.Bars(
                listOf(Diagram.Bar("\"was\"", 0.9f, Tone.MUTED, "£80"), Diagram.Bar("\"now\"", 0.45f, Tone.GOOD, "£40")),
                caption = "The first number we see becomes the anchor: next to £80, £40 feels like a bargain, whatever the thing is worth.",
            ),
            "The Planning Fallacy" to Diagram.Bars(
                listOf(Diagram.Bar("planned", 0.35f, Tone.MUTED, "3 weeks"), Diagram.Bar("actual", 0.85f, Tone.BAD, "7 weeks")),
                caption = "We plan for the best case. Looking at how long similar projects really took gives a truer estimate.",
            ),
            "Parkinson's Law" to Diagram.Bars(
                listOf(Diagram.Bar("given a day", 0.3f, Tone.GOOD, "done in a day"), Diagram.Bar("given a week", 0.9f, Tone.BAD, "takes the week")),
                caption = "Work expands to fill the time available for it.",
            ),
            "Negativity Bias" to Diagram.Bars(
                listOf(Diagram.Bar("one hurtful moment", 0.85f, Tone.BAD, "weighs this"), Diagram.Bar("one kind moment", 0.3f, Tone.GOOD, "weighs this")),
                caption = "Bad experiences weigh more than equally good ones, so kindness needs to be generous.",
            ),
            "The Hierarchy of Needs" to Diagram.Pyramid(
                listOf("food, water, sleep", "safety", "love and belonging", "esteem", "growth"),
                "Maslow suggested that higher needs come into focus once the lower ones are met (in life, the order is often looser).",
            ),
            "Dunbar's Number" to Diagram.Rings(listOf("5 closest", "15 good friends", "50 friends", "150 people we know"), "you",
                "We seem able to keep up stable relationships with about 150 people, in rings of growing size."),
            "The Four Noble Truths" to Diagram.Flow(
                listOf("1. There is suffering", "2. It arises from craving", "3. It can end", "4. The path: the Eightfold Way"), highlight = 3,
                caption = "The Buddha spoke like a doctor: the illness, its cause, the hope of a cure, and the treatment.",
            ),
            "Habit, the Flywheel" to Diagram.Flow(listOf("cue", "routine", "reward"), cycle = true, highlight = 1,
                caption = "Each turn of the loop makes the routine easier, for good habits and bad ones alike.", height = 220f),
            "The Hero's Journey" to Diagram.Flow(listOf("the call", "crossing over", "trials", "the ordeal", "the gift", "the return"), cycle = true, highlight = 5,
                caption = "Departure, trials, transformation and return: a shape Joseph Campbell traced through myths worldwide."),
            "Two Systems" to Diagram.Grid(
                listOf("System 1", "System 2"), listOf("how", "good at"),
                listOf(
                    listOf(Diagram.Cell(listOf("fast, automatic,", "effortless"), Tone.ACCENT), Diagram.Cell(listOf("slow, deliberate,", "tiring"), Tone.GOOD)),
                    listOf(Diagram.Cell(listOf("faces, habits,", "quick guesses")), Diagram.Cell(listOf("sums, logic,", "checking the guess"))),
                ),
                "Most of the time System 1 runs things. Errors creep in when we trust a quick answer that needed slow thought.", height = 190f,
            ),
            "Correlation Is Not Causation" to Diagram.Net(
                listOf("hot weather" to Offset(160f, 40f), "ice-cream sales" to Offset(70f, 150f), "drownings" to Offset(250f, 150f)),
                listOf(Triple(0, 1, false), Triple(0, 2, false), Triple(1, 2, true)),
                mapOf(0 to Tone.GOOD, 1 to Tone.ACCENT, 2 to Tone.ACCENT),
                "Ice cream and drownings rise together, but neither causes the other: the heat drives both.",
            ),
            "Externalities" to Diagram.Net(
                listOf("factory" to Offset(70f, 60f), "buyers" to Offset(250f, 60f), "neighbours" to Offset(160f, 155f)),
                listOf(Triple(0, 1, false), Triple(0, 2, true)),
                mapOf(2 to Tone.BAD),
                "The price paid between factory and buyers doesn't include the smoke that falls on the neighbours.",
            ),
            "Photosynthesis" to Diagram.Net(
                listOf("sunlight" to Offset(60f, 34f), "water" to Offset(160f, 34f), "carbon dioxide" to Offset(260f, 34f), "sugar" to Offset(100f, 160f), "oxygen" to Offset(220f, 160f)),
                listOf(Triple(0, 3, false), Triple(1, 3, false), Triple(2, 3, false), Triple(1, 4, false)),
                mapOf(3 to Tone.GOOD, 4 to Tone.GOOD),
                "Leaves turn light, water and carbon dioxide into sugar, and give off the oxygen we breathe.",
            ),
            "Bayes' Theorem" to Diagram.Dots(
                250, listOf(3 to Tone.BAD) + listOf(17, 41, 66, 88, 104, 131, 152, 178, 199, 223, 240).map { it to Tone.ACCENT },
                listOf("really ill, tests positive" to Tone.BAD, "not ill, but tests positive anyway" to Tone.ACCENT),
                "With a rare illness and a good-but-imperfect test, most positive results are false alarms. How common something is matters as much as the test.",
            ),
            "The Cosmic Calendar" to Diagram.Timeline("the whole history of the universe, as one year",
                listOf("stars and galaxies" to 0.66f, "the Sun and Earth" to 0.1f, "life on Earth" to 0.239f, "us" to 0.001f),
                "all of human history: the last minutes of 31 December",
                "Squeeze 13.8 billion years into a year, and everything people have ever done fits into its final moments."),
            "Deep Time" to Diagram.Timeline("the Earth's 4.5 billion years",
                listOf("before life" to 0.15f, "single cells" to 0.72f, "plants and animals" to 0.129f, "humans" to 0.001f),
                "humans: a sliver at the end",
                "Geologists found the Earth is unimaginably old. Our whole story is a thin line at the edge of it."),
            "The Butterfly Effect" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve(80) { x -> 0.5f + 0.35f * sin(x * 18f) * x }, "forecast A", Tone.ACCENT),
                    Diagram.Series(curve(80) { x -> 0.5f + 0.35f * sin(x * 18f + x * x * 4f) * x }, "same start, rounded", Tone.BAD, dashed = true),
                ),
                caption = "Lorenz rounded one number in his weather model. At first the two runs matched; soon they had nothing in common.",
                xLabels = "now" to "weeks ahead",
            ),
            "Regression to the Mean" to Diagram.Bars(
                listOf(Diagram.Bar("season 1", 0.5f, Tone.MUTED), Diagram.Bar("record season", 0.92f, Tone.GOOD, "luck helped"), Diagram.Bar("next season", 0.58f, Tone.MUTED, "back near usual")),
                caption = "An extreme result usually includes good luck, which rarely repeats, so the next one tends to be more ordinary.",
            ),
            "The Birthday Paradox" to Diagram.Lines(
                listOf(Diagram.Series(curve(60) { x -> val n = (x * 60).toInt(); 1f - (0 until n).fold(1.0) { acc, k -> acc * (365 - k) / 365.0 }.toFloat() }, "chance two share a birthday", Tone.ACCENT)),
                listOf(Diagram.Mark(23f / 60f, 0.507f, "23 people: 50%", left = false)),
                "1 person" to "60 people",
                caption = "With 23 people there are 253 possible pairs, so a shared birthday becomes more likely than not.",
            ),
            "The Tragedy of the Commons" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> (1f - x * x * 1.1f).coerceAtLeast(0.02f) }, "fish left in the sea", Tone.GOOD),
                    Diagram.Series(curve { x -> 0.1f + 0.8f * x }, "boats fishing", Tone.BAD, dashed = true),
                ),
                xLabels = "early years" to "collapse",
                caption = "Each boat gains from one more catch, while the cost of an emptied sea is shared by all.",
            ),
            "The Bystander Effect" to Diagram.Lines(
                listOf(Diagram.Series(curve { x -> 0.85f * exp(-x * 2.4f) + 0.08f }, "chance any one person helps", Tone.ACCENT)),
                listOf(Diagram.Mark(0f, 0.93f, "alone", left = false), Diagram.Mark(1f, 0.16f, "a crowd")),
                "1 witness" to "many witnesses",
                caption = "The more people present, the more each assumes someone else will act. Asking one person directly breaks the spell.",
            ),
            "Goodhart's Law" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> 0.2f + 0.75f * x }, "the number (target)", Tone.GOOD),
                    Diagram.Series(curve { x -> 0.2f + 0.5f * x - 0.6f * (x - 0.4f).coerceAtLeast(0f) }, "what it was meant to show", Tone.BAD, dashed = true),
                ),
                xLabels = "target set" to "later",
                caption = "Once a measure becomes a target, people improve the number, and it stops telling the truth.",
            ),

            // ---- second set ----------------------------------------------------------------------
            "The Pythagorean Theorem" to Diagram.Picture("pythagoras", "On a right-angled triangle with sides 3 and 4, the squares on those sides (9 and 16) add up to the square on the longest side (25).", 230f),
            "Pi" to Diagram.Picture("pi", "Roll any circle out flat: its edge is a little over three times as long as it is wide, every time.", 170f),
            "The Pigeonhole Principle" to Diagram.Picture("pigeonhole", "More things than places means at least one place holds two: simple, but it proves surprising facts.", 190f),
            "The Magical Number Seven" to Diagram.Picture("chunks", "We can hold only a few items in mind at once. Grouping them into chunks lets us hold more.", 190f),
            "Inattentional Blindness" to Diagram.Picture("gorilla", "Busy counting passes, about half the viewers missed a person in a gorilla suit walking through the game.", 195f),
            "Fractals" to Diagram.Picture("fractaltree", "Trees, rivers, coastlines and ferns repeat their shape at smaller and smaller scales.", 205f),
            "Eratosthenes Measures the Earth" to Diagram.Picture("eratosthenes", "At noon the Sun shone straight down a well at Syene, but cast a small shadow at Alexandria. The angle gave the size of the Earth.", 222f),
            "Linear Perspective" to Diagram.Picture("perspective", "Parallel lines running away from us seem to meet at one point on the horizon, and things shrink as they recede.", 210f),
            "Plate Tectonics" to Diagram.Picture("plates", "India's plate is still pushing into Asia, slowly raising the Himalayas.", 215f),
            "Spacetime Curves" to Diagram.Picture("spacetime", "Mass curves space and time around it; the planets are following the curve, not being pulled by a rope.", 220f),
            "Kepler's Laws" to Diagram.Picture("kepler", "Planets move in ellipses and speed up near the Sun, sweeping out equal areas in equal times.", 215f),
            "The Double-Slit Experiment" to Diagram.Picture("doubleslit", "Sent one at a time through two slits, particles still build up bands, as if each went through both.", 222f),
            "The Goldilocks Zone" to Diagram.Picture("goldilocks", "Too close, water boils away; too far, it freezes. In between, a planet could keep liquid water.", 240f),
            "The Iceberg Theory" to Diagram.Picture("iceberg", "Hemingway showed only the tip; what he knew and left out gives the story its weight.", 225f),
            "The Forgetting Curve" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve(60) { x -> exp(-x * 4f) * 0.85f + 0.05f }, "never reviewed", Tone.BAD, dashed = true),
                    Diagram.Series(curve(60) { x -> val d = listOf(0f, 0.08f, 0.22f, 0.5f).last { it <= x }; val k = listOf(0f, 0.08f, 0.22f, 0.5f).indexOf(d); 0.9f * exp(-(x - d) * 4f / (1 + k * 1.6f)) + 0.05f }, "reviewed at 1, 3, 7 days…", Tone.GOOD),
                ),
                xLabels = "learned" to "weeks later",
                caption = "Memory fades fast without review. Each spaced review lifts it back, and it fades more slowly each time.",
            ),
            "Diminishing Returns" to Diagram.Lines(
                listOf(Diagram.Series(curve { x -> 1f - exp(-x * 3.2f) }, "what you get back", Tone.ACCENT)),
                listOf(Diagram.Mark(0.15f, 1f - exp(-0.48f), "first hours: a lot", left = false), Diagram.Mark(0.9f, 0.96f, "later: little more")),
                "a little effort" to "a lot",
                caption = "Each extra hour or extra unit adds less than the one before.",
            ),
            "Marginal Utility" to Diagram.Bars(
                listOf(
                    Diagram.Bar("1st glass", 0.92f, Tone.GOOD, "precious"), Diagram.Bar("2nd", 0.6f, Tone.ACCENT),
                    Diagram.Bar("3rd", 0.35f, Tone.ACCENT), Diagram.Bar("10th", 0.08f, Tone.MUTED, "hardly"),
                ),
                caption = "To a thirsty person the first glass of water is worth a great deal; each one after, less.",
            ),
            "Comparative Advantage" to Diagram.Grid(
                listOf("cloth", "wine"), listOf("England", "Portugal"),
                listOf(
                    listOf(Diagram.Cell(listOf("100 workers"), Tone.GOOD, "England's best"), Diagram.Cell(listOf("120 workers"))),
                    listOf(Diagram.Cell(listOf("90 workers")), Diagram.Cell(listOf("80 workers"), Tone.GOOD, "Portugal's best")),
                ),
                "Ricardo's example: Portugal is better at both, yet both gain if England makes cloth, Portugal makes wine, and they trade.",
            ),
            "Expected Value" to Diagram.Bars(
                listOf(Diagram.Bar("you pay", 0.8f, Tone.BAD, "£1"), Diagram.Bar("average win", 0.6f, Tone.ACCENT, "75p")),
                caption = "Win £3 one time in four: on average 75p back for every £1 paid. In the long run, the game loses.",
            ),
            "The Law of Large Numbers" to Diagram.Lines(
                listOf(
                    Diagram.Series(listOf(0.0f to 1f, 0.03f to 0.5f, 0.06f to 0.67f, 0.1f to 0.4f, 0.15f to 0.6f, 0.22f to 0.45f, 0.3f to 0.56f, 0.4f to 0.47f, 0.52f to 0.53f, 0.66f to 0.49f, 0.8f to 0.51f, 1f to 0.5f).map { Offset(it.first, it.second) }, "share of heads", Tone.ACCENT),
                    Diagram.Series(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f)), "one half", Tone.MUTED, dashed = true),
                ),
                xLabels = "a few tosses" to "thousands",
                caption = "Early results swing wildly; over many tosses the share of heads settles towards one half.",
            ),
            "Tit for Tat" to Diagram.Flow(listOf("Start by cooperating", "Then copy what they did last time", "Forgive quickly when they return to cooperating"), highlight = 0,
                caption = "Nice, retaliating, forgiving and clear: the simple rule that won Axelrod's tournaments."),
            "The Social Contract" to Diagram.Flow(listOf("Life without government", "People agree to common rules", "Government rules with their consent"), highlight = 2,
                caption = "Hobbes, Locke and Rousseau each told the story differently, but all grounded authority in consent."),
            "Self-Fulfilling Prophecy" to Diagram.Flow(listOf("you expect it", "you act as if", "others respond", "it comes true"), cycle = true, highlight = 3,
                caption = "An expectation changes behaviour, and the behaviour brings about what was expected.", height = 230f),
            "Homeostasis" to Diagram.Flow(listOf("too hot", "the body notices", "you sweat", "you cool down", "back to normal"), cycle = true, highlight = 4,
                caption = "Feedback loops keep temperature, sugar and water within safe limits."),
            "How Stars Shine" to Diagram.Flow(listOf("Hydrogen in the core", "Squeezed and heated: fusion", "Helium, plus light and heat"), highlight = 2,
                caption = "Every second the Sun turns about 600 million tonnes of hydrogen into helium."),
            "Implementation Intentions" to Diagram.Flow(listOf("When: after breakfast", "Then: walk for ten minutes"), highlight = 1,
                caption = "Tying a plan to a cue (\"when X, I'll do Y\") makes it far more likely to happen."),
            "Persephone and the Seasons" to Diagram.Flow(listOf("spring: she returns", "summer", "autumn: she leaves", "winter: Demeter grieves"), cycle = true, highlight = 0,
                caption = "Because she ate the pomegranate seeds, Persephone spends part of each year below, and the earth goes cold.", height = 230f),
            "The Eightfold Path" to Diagram.Grid(
                listOf("wisdom", "conduct", "mind"), listOf("the path"),
                listOf(listOf(
                    Diagram.Cell(listOf("right view", "right intention"), Tone.ACCENT),
                    Diagram.Cell(listOf("right speech", "right action", "right livelihood"), Tone.GOOD),
                    Diagram.Cell(listOf("right effort", "mindfulness", "concentration"), Tone.ACCENT),
                )),
                "The Buddha's eight practices are often grouped in three: wisdom, ethical conduct and training the mind.", height = 150f,
            ),
            "The Framing Effect" to Diagram.Grid(
                listOf("framed as saving", "framed as losing"), listOf("600 at risk"),
                listOf(listOf(
                    Diagram.Cell(listOf("200 people", "will be saved"), Tone.GOOD, "most choose this"),
                    Diagram.Cell(listOf("400 people", "will die"), Tone.BAD, "most avoid this"),
                )),
                "The two options are exactly the same; only the wording differs, and people's choices flip.", height = 140f,
            ),
            "Montage and the Kuleshov Effect" to Diagram.Grid(
                listOf("shown next to…", "viewers saw"), listOf("same face", "same face"),
                listOf(
                    listOf(Diagram.Cell(listOf("a bowl of soup")), Diagram.Cell(listOf("hunger"), Tone.ACCENT)),
                    listOf(Diagram.Cell(listOf("a child playing")), Diagram.Cell(listOf("joy"), Tone.GOOD)),
                ),
                "Kuleshov cut the same expressionless face next to different shots, and audiences praised the actor's range.", height = 180f,
            ),
            "The Matthew Effect" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> 0.12f + 0.75f * x.pow(2.2f) }, "an early head start", Tone.ACCENT),
                    Diagram.Series(curve { x -> 0.08f + 0.22f * x.pow(1.4f) }, "no head start", Tone.MUTED, dashed = true),
                ),
                xLabels = "the start" to "years later",
                caption = "A small early advantage attracts more advantage, and the gap widens over time.",
            ),
            "Kaizen" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> (1.01f.pow(x * 365f) - 1f) / 36.8f }, "1% better each day", Tone.GOOD),
                    Diagram.Series(listOf(Offset(0f, 0f), Offset(1f, 0f)), "no change", Tone.MUTED, dashed = true),
                ),
                xLabels = "today" to "a year on",
                caption = "Tiny improvements compound: 1% a day for a year makes something about 37 times better.",
            ),
            "Mere Exposure" to Diagram.Lines(
                listOf(Diagram.Series(curve { x -> 0.2f + 0.65f * (1f - exp(-x * 3f)) }, "how much we like it", Tone.ACCENT)),
                xLabels = "first time seen" to "seen often",
                caption = "In Zajonc's studies, shapes and words were liked more simply for having been seen more often.",
            ),
            "Affective Forecasting" to Diagram.Lines(
                listOf(
                    Diagram.Series(curve { x -> if (x < 0.2f) 0.3f else 0.85f - 0.1f * (x - 0.2f) }, "how happy we expect to be", Tone.MUTED, dashed = true),
                    Diagram.Series(curve { x -> 0.3f + if (x < 0.2f) 0f else 0.55f * exp(-(x - 0.2f) * 6f) }, "how happy we actually feel", Tone.ACCENT),
                ),
                xLabels = "the good news" to "months later",
                caption = "We expect joys (and sorrows) to last; in fact we adapt much faster than we predict.",
            ),
            "The Strength of Weak Ties" to Diagram.Net(
                listOf("you" to Offset(70f, 95f), "close friends" to Offset(70f, 165f), "an acquaintance" to Offset(170f, 40f), "a new world" to Offset(260f, 110f), "the job" to Offset(260f, 175f)),
                listOf(Triple(0, 1, false), Triple(0, 2, false), Triple(2, 3, false), Triple(3, 4, false)),
                mapOf(0 to Tone.GOOD, 4 to Tone.GOOD),
                "Close friends know what you know. Acquaintances bridge to other circles, and that's where new chances come from.", height = 200f,
            ),
            "The Halo Effect" to Diagram.Net(
                listOf("good-looking" to Offset(70f, 100f), "kind?" to Offset(240f, 35f), "clever?" to Offset(250f, 100f), "honest?" to Offset(240f, 165f)),
                listOf(Triple(0, 1, false), Triple(0, 2, false), Triple(0, 3, false)),
                mapOf(0 to Tone.ACCENT, 1 to Tone.MUTED, 2 to Tone.MUTED, 3 to Tone.MUTED),
                "One visible trait shines onto others we have no evidence for.", height = 195f,
            ),
            "Mass Extinctions" to Diagram.Bars(
                listOf(
                    Diagram.Bar("Ordovician", 0.85f, Tone.ACCENT, "~85%"), Diagram.Bar("Devonian", 0.75f, Tone.ACCENT, "~75%"),
                    Diagram.Bar("Permian", 0.95f, Tone.BAD, "~95%"), Diagram.Bar("Triassic", 0.8f, Tone.ACCENT, "~80%"),
                    Diagram.Bar("Cretaceous", 0.76f, Tone.ACCENT, "~76%"),
                ),
                caption = "Five times, most species vanished in a geological instant. The worst, the end-Permian, took about 95% of sea life.",
            ),
            "Binary" to Diagram.Grid(
                listOf("fours", "twos", "ones"), listOf("5 ="),
                listOf(listOf(Diagram.Cell(listOf("1"), Tone.GOOD), Diagram.Cell(listOf("0")), Diagram.Cell(listOf("1"), Tone.GOOD))),
                "In binary each place is worth double the one to its right: 4 + 0 + 1 = 5, written 101.", height = 120f,
            ),
        )
    }

    fun of(title: String): Diagram? = all[title]

    /** The titles that have a diagram (for tests). */
    val titles: Set<String> get() = all.keys
}
