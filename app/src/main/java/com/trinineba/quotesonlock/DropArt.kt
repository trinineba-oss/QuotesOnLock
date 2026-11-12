package com.trinineba.quotesonlock

/**
 * A single drop (Android 13+, drawn per pixel on the GPU): seen from near the water, one drop falls every
 * [PERIOD] seconds into still, dark water. It dents the surface, a glassy column springs up and throws off a
 * droplet that falls back, and fine rings spread out, bending the reflections of soft out-of-focus lights.
 * The water is a height field of three damped ring packets per drop (the last drop's are still fading); its
 * slope bends the mirrored backdrop and lights glints. tools/drop_still.py renders the same maths for the
 * still pictures (drop_still.jpg, drop_frame.jpg).
 */
internal object DropArt {
    const val PERIOD = 5.0f

    const val SHADER = """
uniform float2 res;
uniform float time;

const float VH = 0.40;
const float VI = 0.60;

float h1(float i, float k) { return fract(sin(i * 1.37 + k * 0.71 + 0.3) * 43758.5453); }

float3 backdrop(float2 p, float asp) {
    float t = clamp(p.y / VH, 0.0, 1.0);
    float3 c = mix(float3(0.0235, 0.0706, 0.1098), float3(0.0706, 0.2039, 0.2667), t);
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float bx = h1(fi, 1.0) * 1.2 - 0.1;
        float by = h1(fi, 2.0) * 0.45 - 0.05;
        float br = 0.03 + 0.06 * h1(fi, 3.0);
        float k = h1(fi, 4.0);
        float3 bc = k < 0.25 ? float3(0.949, 0.714, 0.420) : (k < 0.5 ? float3(0.910, 0.604, 0.353) : (k < 0.75 ? float3(0.624, 0.839, 0.878) : float3(1.0, 0.886, 0.659)));
        float ba = 0.25 + 0.45 * h1(fi, 5.0);
        float d = length(float2((p.x - bx) * asp, p.y - by)) / br;
        float q = clamp(1.2 - d, 0.0, 1.0);
        c += bc * (ba * pow(q, 1.5) * 0.55);
    }
    return c;
}

float packet(float R, float tau, float c, float lam, float A, float dl) {
    float ph = R - c * max(tau - dl, 0.0);
    float env = exp(-(ph / 0.35) * (ph / 0.35)) * step(ph, 0.25);
    return A * env * cos(6.2831853 * ph / lam) * exp(-tau / 3.2) / sqrt(1.0 + 4.0 * R);
}

float heightOne(float2 uv, float tau) {
    if (tau < 0.0) return 0.0;
    float dz = 1.0 / max(uv.y - VH, 0.001);
    float X = (uv.x - 0.5) * dz * 0.9;
    float Z0 = 1.0 / (VI - VH);
    float R = sqrt(X * X + (dz - Z0) * (dz - Z0));
    float h = packet(R, tau, 0.55, 0.22, 0.030, 0.0) + packet(R, tau, 0.42, 0.16, 0.018, 0.10) + packet(R, tau, 0.30, 0.12, 0.010, 0.22);
    h += -0.05 * exp(-tau / 0.18) * exp(-(R / 0.12) * (R / 0.12));
    return h;
}

float heightAt(float2 uv, float tau) { return heightOne(uv, tau) + heightOne(uv, tau + 5.0); }

float3 sphere(float3 img, float2 uv, float asp, float cx, float cy, float r, float stretch, float alpha) {
    float dx = (uv.x - cx) * asp;
    float dy = (uv.y - cy) / stretch;
    float d = length(float2(dx, dy)) / r;
    if (d >= 1.0) return img;
    float n = sqrt(max(1.0 - d * d, 0.0));
    float3 inside = backdrop(float2(cx - (uv.x - cx) * 0.35, clamp(cy - (uv.y - cy) * 0.35 + 0.08, 0.0, 0.39)), asp);
    float rim = pow(1.0 - n, 1.5);
    float3 c = inside * (0.55 + 0.6 * n) * (1.0 - 0.75 * rim);
    float ax = dx / r + 0.35; float ay = dy / r + 0.45;
    float bx = dx / r - 0.3; float by = dy / r - 0.55;
    c += float3(1.0) * exp(-(ax * ax + ay * ay) / 0.03) * 1.2 + float3(0.949, 0.714, 0.420) * exp(-(bx * bx + by * by) / 0.05) * 0.35;
    float a = clamp((1.0 - d) * r * res.y / 1.5, 0.0, 1.0) * alpha;
    return mix(img, c, a);
}

half4 main(float2 fc) {
    float2 uv = fc / res;
    float asp = res.x / res.y;
    float tau = mod(time, 5.0) - 0.9;
    float3 col;
    if (uv.y > VH) {
        float du = 1.0 / 360.0; float dv = 1.0 / 780.0;
        float gx = (heightAt(uv + float2(du, 0.0), tau) - heightAt(uv - float2(du, 0.0), tau)) * 0.5;
        float gy = (heightAt(uv + float2(0.0, dv), tau) - heightAt(uv - float2(0.0, dv), tau)) * 0.5;
        float2 r = float2(uv.x + gx * 33.0, clamp((VH - (uv.y - VH) * 0.85) + gy * 55.0, -0.2, VH));
        float fres = clamp(0.25 + 0.75 * exp(-(uv.y - VH) * 3.0), 0.0, 1.0);
        col = mix(float3(0.0157, 0.0627, 0.0863), backdrop(r, asp), fres);
        col += float3(1.0, 0.945, 0.847) * pow(max(-gy * 120.0 + gx * 40.0, 0.0), 2.2) * 0.9;
    } else {
        col = backdrop(uv, asp);
    }
    // The drop, falling straight down, and its faint reflection.
    float tfall = tau + 0.9;
    if (tfall >= 0.0 && tfall < 0.9) {
        float p = tfall / 0.9;
        float cy = -0.05 + (VI - 0.012 + 0.05) * p * p;
        col = sphere(col, uv, asp, 0.5, cy, 0.022, 1.0 + 0.25 * p, 1.0);
        float ry = VH + (VH - cy) * 0.85;
        if (ry > VH) col = sphere(col, uv, asp, 0.5, ry, 0.020, 1.0, 0.25 * p);
    }
    // The column that springs up after the impact.
    if (tau > 0.05 && tau < 1.0) {
        float q = (tau - 0.05) / 0.95;
        float hgt = 0.085 * pow(max(sin(3.14159265 * q), 0.0), 0.8);
        if (hgt > 0.004) {
            float rel = clamp((VI - uv.y) / hgt, 0.0, 1.0);
            float wdt = 0.016 - 0.006 * rel + 0.010 * exp(-(VI - uv.y) / 0.012);
            float dx = (uv.x - 0.5) * asp;
            if (uv.y < VI + 0.004 && uv.y > VI - hgt && abs(dx) < wdt) {
                float s = dx / max(wdt, 0.0001);
                float n = sqrt(clamp(1.0 - s * s, 0.0, 1.0));
                float3 inside = backdrop(float2(0.5 - (uv.x - 0.5) * 0.5, clamp(0.30 - (VI - uv.y) * 0.6, 0.0, 0.39)), asp);
                float rim = pow(1.0 - n, 1.4);
                float3 c = inside * (0.6 + 0.7 * n) * (1.0 - 0.7 * rim);
                c += float3(1.0, 0.965, 0.902) * exp(-(s + 0.4) * (s + 0.4) / 0.015) * 0.9;
                c += float3(0.949, 0.714, 0.420) * exp(-(s - 0.55) * (s - 0.55) / 0.03) * 0.25;
                float a2 = clamp((wdt - abs(dx)) * 780.0 / 1.2, 0.0, 1.0);
                col = mix(col, c, a2);
            }
            col = sphere(col, uv, asp, 0.5, VI - hgt, 0.0105, 1.0, 1.0);
        }
    }
    // A droplet pinches off the top, rises a little and falls back in.
    if (tau > 0.42 && tau < 1.55) {
        float s2 = (tau - 0.42) / 1.13;
        float y0 = VI - 0.085 * pow(sin(3.14159265 * (0.37 / 0.95)), 0.8) - 0.012;
        float y = s2 < 0.625 ? y0 - 0.05 * sin(3.14159265 * min(s2 * 1.6, 1.0)) : y0 + ((s2 - 0.625) / 0.375) * ((s2 - 0.625) / 0.375) * (VI - y0);
        col = sphere(col, uv, asp, 0.5, y, 0.0085, s2 > 0.625 ? 1.2 : 1.0, 1.0);
    }
    col = pow(clamp(col, 0.0, 1.0), float3(1.0 / 1.15));
    return half4(half3(col), 1.0);
}
"""
}
