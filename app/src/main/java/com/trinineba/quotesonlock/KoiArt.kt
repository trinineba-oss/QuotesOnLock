package com.trinineba.quotesonlock

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The koi pond (Android 13+, drawn per pixel on the GPU): five koi of different kinds (kohaku, sanke, showa,
 * ogon and a deep chagoi) glide in slow curves over a pebble floor where sunlight ripples. Each fish's spine is
 * worked out on the CPU every frame ([bind]) as [N] points with a radius each; the shader shapes the body from
 * them (a chain of tapered capsules), paints its pattern, scales, eyes and translucent fins, and its shadow.
 * Lily pads are drawn over it on the canvas ([drawPads]). Sizes are in a 360-unit-wide space.
 */
internal object KoiArt {
    const val N = 16

    private class Koi(val c1: Int, val c2: Int, val c3: Int?, val ph: Float, val sp: Float, val len: Float, val depth: Float)

    // Deepest first, so nearer fish are painted over them.
    private val fish = listOf(
        Koi(0xB88A5A.toInt(), 0xD9B488.toInt(), null, 5.6f, 0.7f, 0.27f, 0.8f),
        Koi(0x1A1A1A, 0xE0552E.toInt(), 0xF3EFE6.toInt(), 3.3f, 1.1f, 0.30f, 0.55f),
        Koi(0xF2EFE8.toInt(), 0xD94A2A.toInt(), 0x161616, 1.7f, 0.8f, 0.32f, 0.35f),
        Koi(0xE8B048.toInt(), 0xF6D27A.toInt(), null, 4.6f, 0.9f, 0.34f, 0.25f),
        Koi(0xF6F1EA.toInt(), 0xE2532E.toInt(), null, 0.0f, 1.0f, 0.36f, 0.15f),
    )

    private val pts = FloatArray(5 * N * 2)
    private val rad = FloatArray(5 * N)
    private val shape = FloatArray(5 * 4)
    private val extra = FloatArray(5 * 4)
    private val c1 = FloatArray(15)
    private val c2 = FloatArray(15)
    private val c3 = FloatArray(15)
    private val has3 = FloatArray(5)
    private val box = FloatArray(5 * 4)
    private val floorMatrix = android.graphics.Matrix()
    private val xy = FloatArray(2)

    /** Where a fish's head is at time [t], into [xy] (no allocation: this runs every frame). */
    private fun path(ph: Float, sp: Float, t: Float) {
        val a = t * 0.16f * sp + ph
        xy[0] = 0.5f + 0.34f * sin(a) + 0.06f * sin(a * 2.3f + ph)
        xy[1] = 0.5f + 0.38f * sin(a * 0.7f + ph * 1.3f) + 0.05f * cos(a * 1.9f)
    }

    private fun rgb(out: FloatArray, i: Int, c: Int) {
        out[i * 3] = ((c shr 16) and 255) / 255f
        out[i * 3 + 1] = ((c shr 8) and 255) / 255f
        out[i * 3 + 2] = (c and 255) / 255f
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
    fun bind(shader: android.graphics.RuntimeShader, floor: android.graphics.BitmapShader, fw: Int, fh: Int, w: Float, h: Float, t: Float) {
        val s = maxOf(w / fw, h / fh)
        floorMatrix.setScale(s, s)
        floorMatrix.postTranslate((w - fw * s) / 2f, (h - fh * s) / 2f)
        floor.setLocalMatrix(floorMatrix)
        shader.setInputShader("pond", floor)
        val sw = 360f
        val sh = 360f * h / w
        for ((f, k) in fish.withIndex()) {
            path(k.ph, k.sp, t)
            val x = xy[0]; val y = xy[1]
            path(k.ph, k.sp, t - 0.3f)
            val x2 = xy[0]; val y2 = xy[1]
            val ang = atan2((y - y2) * sh, (x - x2) * sw)
            val ca = cos(ang)
            val sa = sin(ang)
            val l = k.len * sw * (1.1f - 0.35f * k.depth)
            for (i in 0 until N) {
                val q = i / (N - 1f)
                val wig = sin(t * 4.2f * k.sp - q * 4.5f + k.ph) * 0.075f * q.pow(1.4f) * l
                pts[(f * N + i) * 2] = x * sw - ca * q * l - sa * wig
                pts[(f * N + i) * 2 + 1] = y * sh - sa * q * l + ca * wig
                val prof = if (q < 0.06f) sqrt(q / 0.06f) else 1f
                rad[f * N + i] = max(0.135f * l * prof * (1f - 0.78f * max(0f, (q - 0.30f) / 0.70f).pow(1.25f)), 0.012f * l)
            }
            // The box this fish (with its fins and shadow) can touch; pixels outside it skip the fish.
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until N) {
                val px = pts[(f * N + i) * 2]; val py = pts[(f * N + i) * 2 + 1]
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
            }
            val pad = 0.4f * l
            box[f * 4] = minX - pad; box[f * 4 + 1] = minY - pad
            box[f * 4 + 2] = maxX + pad + 10f; box[f * 4 + 3] = maxY + pad + 15f
            shape[f * 4] = ca; shape[f * 4 + 1] = sa; shape[f * 4 + 2] = l; shape[f * 4 + 3] = k.depth
            extra[f * 4] = k.ph
            extra[f * 4 + 1] = sin(t * 4.2f * k.sp - 4.5f + k.ph)
            extra[f * 4 + 2] = if (k.c1 == 0xE8B048.toInt() || k.c1 == 0xB88A5A.toInt()) 1f else 0f
            extra[f * 4 + 3] = if (k.c1 == 0x1A1A1A) 1f else 0f
            rgb(c1, f, k.c1); rgb(c2, f, k.c2); rgb(c3, f, k.c3 ?: k.c1)
            has3[f] = if (k.c3 != null) 1f else 0f
        }
        shader.setFloatUniform("res", w, h)
        shader.setFloatUniform("time", t % 3600f)
        shader.setFloatUniform("P", pts)
        shader.setFloatUniform("R", rad)
        shader.setFloatUniform("F", shape)
        shader.setFloatUniform("G", extra)
        shader.setFloatUniform("C1", c1)
        shader.setFloatUniform("C2", c2)
        shader.setFloatUniform("C3", c3)
        shader.setFloatUniform("HAS3", has3)
        shader.setFloatUniform("BB", box)
    }

    // ---- lily pads, drawn over the water ----

    private val pads = floatArrayOf(0.20f, 0.16f, 44f, 0.82f, 0.28f, 36f, 0.70f, 0.80f, 52f, 0.14f, 0.70f, 34f, 0.52f, 0.50f, 24f)
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()
    private var blurFor = -1f
    private var shadowBlur: BlurMaskFilter? = null

    fun drawPads(c: Canvas, w: Float, h: Float, t: Float) {
        val k = w / 360f
        if (blurFor != k) { shadowBlur = BlurMaskFilter(7f * k, BlurMaskFilter.Blur.NORMAL); blurFor = k }
        for (i in 0 until pads.size / 3) {
            val cx = pads[i * 3] * w + sin(t * 0.3f + i) * 3f * k
            val cy = pads[i * 3 + 1] * h + cos(t * 0.25f + i) * 3f * k
            val r = pads[i * 3 + 2] * k
            val st = 40f + i * 70f
            padPaint.color = 0x6E000A05
            padPaint.maskFilter = shadowBlur
            oval.set(cx - r + 10f * k, cy - r + 12f * k, cx + r + 10f * k, cy + r + 12f * k)
            c.drawOval(oval, padPaint)
            padPaint.maskFilter = null
            padPaint.color = 0xFF36623A.toInt()
            oval.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(oval, st + 12f, 336f, true, padPaint)
            padPaint.color = 0xFF427442.toInt()
            oval.set(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f)
            c.drawArc(oval, st + 14f, 332f, true, padPaint)
            vein.color = 0xC8588C52.toInt()
            vein.strokeWidth = 1f * k
            for (v in 0 until 11) {
                val a = Math.toRadians((st + 22f + v * 29f).toDouble()).toFloat()
                c.drawLine(cx, cy, cx + cos(a) * r * 0.88f, cy + sin(a) * r * 0.88f, vein)
            }
            padPaint.color = 0x2DA0C896
            oval.set(cx - r * 0.55f, cy - r * 0.75f, cx - r * 0.05f, cy - r * 0.35f)
            c.drawOval(oval, padPaint)
            if (i == 0 || i == 2) {
                padPaint.color = 0xFFF8CEDA.toInt()
                for (p in 0 until 10) {
                    val a = p * Math.PI.toFloat() / 5f + i
                    c.drawCircle(cx + cos(a) * r * 0.2f, cy + sin(a) * r * 0.2f, r * 0.17f, padPaint)
                }
                padPaint.color = 0xFFFCE4EA.toInt()
                for (p in 0 until 6) {
                    val a = p * Math.PI.toFloat() / 3f + i + 0.3f
                    c.drawCircle(cx + cos(a) * r * 0.1f, cy + sin(a) * r * 0.1f, r * 0.12f, padPaint)
                }
                padPaint.color = 0xFFFAD66E.toInt()
                c.drawCircle(cx, cy, r * 0.08f, padPaint)
            }
        }
    }

    const val SHADER = """
uniform shader pond;
uniform float2 res;
uniform float time;
uniform float2 P[80];
uniform float R[80];
uniform float4 BB[5];
uniform float4 F[5];
uniform float4 G[5];
uniform float3 C1[5];
uniform float3 C2[5];
uniform float3 C3[5];
uniform float HAS3[5];

half4 main(float2 fc) {
    float k = res.x / 360.0;
    float2 p = fc / k;
    float2 uv = fc / res;
    float3 col = pond.eval(fc).rgb;
    float n1 = sin(uv.x * 26.0 + time * 0.45 + 2.2 * sin(uv.y * 15.0 - time * 0.3));
    float n2 = sin(uv.y * 29.0 - time * 0.38 + 2.2 * sin(uv.x * 21.0 + time * 0.33));
    float cz = pow(clamp(1.0 - abs(n1 + n2) * 0.8, 0.0, 1.0), 5.0);
    col += float3(0.812, 0.941, 0.784) * 0.09 * cz;

    // soft shadows on the floor, offset down and to the right
    float shade = 0.0;
    for (int f = 0; f < 5; f++) {
        float4 bb = BB[f];
        if (p.x > bb.x && p.x < bb.z && p.y > bb.y && p.y < bb.w) {
            float2 q = p - float2(9.0, 14.0);
            float best = 1.0e9;
            for (int i = 0; i < 15; i++) {
                float2 a = P[f * 16 + i];
                float2 b = P[f * 16 + i + 1];
                float2 pa = q - a; float2 ba = b - a;
                float hh = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
                float d = length(pa - ba * hh) - mix(R[f * 16 + i], R[f * 16 + i + 1], hh);
                best = min(best, d);
            }
            shade = max(shade, smoothstep(9.0, -6.0, best) * 0.5 * (1.0 - F[f].w * 0.6));
        }
    }
    col *= 1.0 - shade * 0.8;

    for (int f = 0; f < 5; f++) {
      float4 bb = BB[f];
      if (p.x > bb.x && p.x < bb.z && p.y > bb.y && p.y < bb.w) {
        float2 dir = float2(F[f].x, F[f].y);
        float2 nrm = float2(-dir.y, dir.x);
        float L = F[f].z;
        float depth = F[f].w;
        float ph = G[f].x;
        float best = 1.0e9; float bq = 0.0; float br = 1.0; float bside = 1.0;
        for (int i = 0; i < 15; i++) {
            float2 a = P[f * 16 + i];
            float2 b = P[f * 16 + i + 1];
            float2 pa = p - a; float2 ba = b - a;
            float hh = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
            float rr = mix(R[f * 16 + i], R[f * 16 + i + 1], hh);
            float d = length(pa - ba * hh) - rr;
            if (d < best) {
                best = d; br = rr; bq = (float(i) + hh) / 15.0;
                bside = sign(dot(pa - ba * hh, nrm) + 0.0001);
            }
        }
        float n = clamp(-best / max(br, 0.001), 0.0, 1.0);
        float lat = (1.0 - n) * bside;
        float pat = sin(bq * 8.0 + ph * 3.0 + lat * 2.2) + 0.7 * sin(bq * 15.0 - ph + lat * 3.5);
        float3 cc = mix(C1[f], C2[f], clamp((pat - 0.45) * 2.0, 0.0, 1.0));
        if (HAS3[f] > 0.5) {
            float spots = sin(bq * 31.0 + ph * 5.0) * sin(lat * 7.0 + bq * 9.0);
            cc = mix(cc, C3[f], clamp((spots - 0.55) * 3.0, 0.0, 1.0) * step(0.12, bq));
        }
        if (bq < 0.07 && G[f].w < 0.5) cc = C1[f];
        float sc = 0.5 + 0.5 * sin(bq * L * 0.55 + lat * 6.0) * sin(bq * L * 0.55 - lat * 6.0);
        cc = cc * (0.52 + 0.55 * sqrt(n)) * (0.94 + 0.06 * sc);
        cc += 0.30 * exp(-((lat + 0.25) / 0.22) * ((lat + 0.25) / 0.22)) * step(0.08, n);
        if (G[f].z > 0.5) cc += 0.12 * exp(-((lat + 0.1) / 0.35) * ((lat + 0.1) / 0.35));
        // eyes
        float2 head = P[f * 16 + 1];
        for (int e = 0; e < 2; e++) {
            float s = e == 0 ? 1.0 : -1.0;
            float2 ep = head + nrm * s * 0.0765 * L;
            float ed = length(p - ep) / (0.012 * L);
            cc *= 1.0 - 0.8 * exp(-ed * ed);
        }
        float alpha = clamp(-best / (1.0 + 3.0 * depth), 0.0, 1.0);
        // tail fan and side fins: translucent, with streaks
        float2 bdir = -dir;
        float2 tail = P[f * 16 + 15];
        float2 rr2 = p - tail;
        float along = dot(rr2, bdir);
        float across = dot(rr2, nrm) - G[f].y * 0.10 * L * clamp(along / (0.3 * L), 0.0, 1.0);
        float tl = 0.32 * L;
        float fa = 0.0;
        if (along > -0.02 * L && along < tl && abs(across) < along * 0.85 + 0.02 * L) {
            float ang = atan(across, max(along, 0.001));
            fa = 0.62 * pow(clamp(1.0 - along / tl, 0.05, 1.0), 0.7) * (0.75 + 0.25 * sin(ang * 22.0));
        }
        float2 base = P[f * 16 + 2];
        for (int e = 0; e < 2; e++) {
            float s = e == 0 ? 1.0 : -1.0;
            float flap = 0.7 + 0.3 * sin(time * 2.5 + ph + s);
            float2 fd = normalize(bdir * 0.55 + nrm * s * 0.83 * flap);
            float2 r3 = p - base;
            float al = dot(r3, fd);
            float ac = dot(r3, float2(-fd.y, fd.x));
            if (al > 0.0 && al < 0.2 * L && abs(ac) < al * 0.55) {
                fa = max(fa, 0.5 * clamp(1.0 - al / (0.2 * L), 0.1, 1.0) * (0.8 + 0.2 * sin(atan(ac, max(al, 0.001)) * 14.0)));
            }
        }
        float3 fincol = (G[f].w > 0.5 ? C2[f] : C1[f]) * 0.95;
        if (alpha <= 0.02) cc = fincol;
        alpha = max(alpha, fa);
        cc = mix(cc, float3(0.114, 0.247, 0.2), 0.45 * depth);
        col = mix(col, cc, alpha * (1.0 - 0.25 * depth));
      }
    }

    col += float3(0.902, 1.0, 0.878) * 0.04 * cz + float3(0.749, 0.847, 0.878) * 0.05 * clamp(1.0 - uv.y * 1.6, 0.0, 1.0);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
