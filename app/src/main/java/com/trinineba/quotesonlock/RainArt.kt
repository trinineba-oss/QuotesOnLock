package com.trinineba.quotesonlock

/**
 * Rain on a window (Rainy day and Rainy night), drawn per pixel on the GPU (Android 13+). The view outside
 * ([scene], with a fogged copy in [fog]) sits behind faint falling rain; on the glass are still
 * droplets and a few drops that slide down in bursts, clearing a trail and leaving beads. Each
 * drop is a small lens showing an upside-down, wider view of the scene. A Python copy of this
 * maths renders the still pictures (rain_still.jpg, rain_frame.jpg) so they match.
 */
internal object RainArt {
    /** Points the shader at the scene and fog pictures (cover-scaled to the screen) and sets the clock. */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
    fun bind(
        shader: android.graphics.RuntimeShader,
        scene: android.graphics.BitmapShader,
        fog: android.graphics.BitmapShader,
        imageWidth: Int,
        imageHeight: Int,
        w: Float,
        h: Float,
        seconds: Float,
        streaks: Float,
        lensLift: Float,
    ) {
        val s = maxOf(w / imageWidth, h / imageHeight)
        val m = android.graphics.Matrix().apply {
            setScale(s, s)
            postTranslate((w - imageWidth * s) / 2f, (h - imageHeight * s) / 2f)
        }
        scene.setLocalMatrix(m)
        fog.setLocalMatrix(m)
        shader.setInputShader("scene", scene)
        shader.setInputShader("fog", fog)
        shader.setFloatUniform("res", w, h)
        // Wrap the clock so the sliding drops keep full precision on long sessions.
        shader.setFloatUniform("time", seconds % 3600f)
        shader.setFloatUniform("streakAmt", streaks)
        shader.setFloatUniform("lensLift", lensLift)
    }

    const val SHADER = """
uniform shader scene;
uniform shader fog;
uniform float2 res;
uniform float time;
uniform float streakAmt;
uniform float lensLift;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Faint rain falling in the distance: thin slanted streaks in random columns.
float streaks(float2 uv, float t, float dens, float speed, float seed) {
    float2 q = float2(uv.x + uv.y * 0.08, uv.y);
    float col = floor(q.x * dens);
    float h = hash12(float2(col, seed));
    float fx = fract(q.x * dens);
    float line = 1.0 - smoothstep(0.0, 0.06, abs(fx - 0.5));
    float fy = fract(q.y * 2.2 - t * speed + h * 10.0);
    float seg = smoothstep(0.0, 0.05, fy) * (1.0 - smoothstep(0.2, 0.35, fy));
    return line * seg * step(0.55, h);
}

// A drop as a lens: d is the offset from its centre and r its radius (both in height units).
half4 lens(float2 p, float2 d, float r) {
    float2 n = d / r;
    float2 c = p - d * res.y;
    half4 col = scene.eval(c - n * r * res.y * 3.0);
    col.rgb = col.rgb * 1.06 + half3(0.03) + fog.eval(p).rgb * half(lensLift);
    float nd = length(float2(n.x, n.y / 1.15));
    float rim = pow(clamp((nd - 0.75) / 0.25, 0.0, 1.0), 1.5);
    col.rgb *= half(1.0 - 0.45 * rim);
    float2 hp = n - float2(-0.3, -0.4);
    col.rgb += half(exp(-dot(hp, hp) / 0.03) * 0.5);
    return col;
}

float inside(float2 d, float r) {
    return step(length(float2(d.x, d.y / 1.15)), r) * step(0.0001, r);
}

half4 main(float2 p) {
    float2 uv = p / res.y;
    half4 base = fog.eval(p);
    float s = (streaks(uv, time, 90.0, 1.6, 3.0) * 0.10 + streaks(uv * 1.3, time, 140.0, 1.1, 9.0) * 0.07) * streakAmt;
    base.rgb += half3(half(s));

    // One sliding drop per column, moving down in bursts and wobbling a little.
    float colw = 0.12;
    float cid = floor(uv.x / colw);
    float sp = 0.035 + 0.04 * hash12(float2(cid, 19.0));
    float ph = hash12(float2(cid, 31.0));
    float yc = fract(ph + time * sp + 0.012 * sin(time * 1.7 + cid * 2.3)) * 1.6 - 0.3;
    float xc = (cid + 0.3 + 0.4 * hash12(float2(cid, 7.0))) * colw + 0.006 * sin(uv.y * 40.0 + cid);
    float rs = 0.012 + 0.006 * hash12(float2(cid, 53.0));
    float2 ds = float2(uv.x - xc, uv.y - yc);
    float above = yc - uv.y;
    float trail = step(0.0, above) * step(above, 0.35) * step(abs(ds.x), rs * 0.75);
    float clear = trail * 0.55 * (1.0 - above / 0.35);
    base = mix(base, scene.eval(p) + fog.eval(p) * half(lensLift * 0.7), half(clear));
    if (inside(ds, rs) > 0.5) return lens(p, ds, rs);

    // Beads left behind in the trail.
    float bcell = floor(uv.y / 0.04);
    float by = (bcell + 0.3 + 0.4 * hash12(float2(cid, bcell))) * 0.04;
    float bx = (cid + 0.3 + 0.4 * hash12(float2(cid, 7.0))) * colw + 0.006 * sin(by * 40.0 + cid) + (hash12(float2(bcell, cid)) - 0.5) * rs;
    float rb = 0.0035 * (0.6 + 0.8 * hash12(float2(bcell, cid + 5.0)));
    float bAge = yc - by;
    float2 db = float2(uv.x - bx, uv.y - by);
    if (bAge > 0.0 && bAge < 0.35 && inside(db, rb) > 0.5) return lens(p, db, rb);

    // Still droplets in two sizes.
    for (int layer = 0; layer < 2; layer++) {
        float cell = layer == 0 ? 0.045 : 0.028;
        float seed = layer == 0 ? 1.0 : 5.0;
        float2 id = floor(uv / cell);
        float2 h = float2(hash12(id + seed), hash12(id + seed + 17.0));
        float present = step(layer == 0 ? 0.45 : 0.55, hash12(id + seed + 41.0));
        float r = cell * (0.10 + 0.22 * hash12(id + seed + 73.0)) * present;
        float2 d = uv - (id + 0.25 + 0.5 * h) * cell;
        if (inside(d, r) > 0.5) return lens(p, d, r);
    }
    return base;
}
"""
}
