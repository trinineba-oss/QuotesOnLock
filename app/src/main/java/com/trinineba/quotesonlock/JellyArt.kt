package com.trinineba.quotesonlock

/**
 * The deep (Android 13+, drawn per pixel on the GPU): one jellyfish alone in dark water, as if it were the only
 * creature in the universe. Its translucent bell contracts quickly and releases slowly, about every eight seconds;
 * a ring of tiny lights twinkles along its margin; fine beaded tentacles trail and sway in waves that travel down
 * them, with four frilled oral arms between; it drifts and tilts slowly while sparse marine snow falls past.
 * Everything is sized from the shorter side, so in landscape it is a small, distant light in a wide dark.
 * tools/jelly_still/jelly.frag is the same maths in WebGL, used to render jelly_still.jpg and jelly_frame.jpg.
 */
internal object JellyArt {
    const val SHADER = """
uniform float2 res;
uniform float time;

float hash(float2 p){ return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 frag) {
  float u = min(res.x, res.y * 0.56);
  float t = time;
  float vy = frag.y / res.y;
  // the deep
  float3 col = mix(float3(0.012, 0.045, 0.090), float3(0.001, 0.004, 0.012), smoothstep(0.0, 0.95, vy));
  float rx = frag.x / res.x - 0.5;
  float shaft = exp(-pow((rx - 0.18 * vy + 0.04 * sin(t * 0.05)) * 4.0, 2.0)) * pow(1.0 - vy, 2.0);
  col += float3(0.20, 0.40, 0.55) * shaft * 0.05;
  // marine snow: sparse, slow, at several depths
  for (int L = 0; L < 3; L++) {
    float fl = float(L);
    float cell = u * (0.16 - fl * 0.04);
    float2 q = frag + float2(sin(t * 0.04 + fl * 2.0) * u * 0.015, -t * u * (0.004 + fl * 0.003));
    float2 id = floor(q / cell);
    float2 f = q - id * cell;
    float h = hash(id + fl * 17.0);
    if (h > 0.72) {
      float2 pt = float2(hash(id + 3.1), hash(id + 7.7)) * cell;
      float d = length(f - pt);
      float r = u * (0.0018 + fl * 0.0014) * (0.6 + hash(id + 9.0));
      float tw = 0.6 + 0.4 * sin(t * (0.3 + h) + h * 30.0);
      col += float3(0.50, 0.62, 0.70) * (0.06 + fl * 0.06) * tw * exp(-d * d / (r * r));
    }
  }
  // the jellyfish, pulsing: a quick contraction and a slow release
  float ph = fract(t * 0.13);
  float pulse = ph < 0.3 ? smoothstep(0.0, 0.3, ph) : 1.0 - smoothstep(0.3, 1.0, ph);
  float drift = t * 0.13 + 0.0;
  float2 c = float2(res.x * (0.50 + 0.10 * sin(t * 0.043)), res.y * (0.38 + 0.04 * sin(t * 0.031)));
  float ang = 0.14 * sin(t * 0.067 + 1.0) + 0.05 * sin(t * 0.23);
  float ca = cos(ang), sa = sin(ang);
  float2 p = frag - c;
  p = float2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
  float R = u * 0.25;
  float w = R * (1.0 - 0.16 * pulse);
  float hgt = R * 0.86 * (1.0 + 0.14 * pulse);
  float3 cyan = float3(0.42, 0.80, 1.00);
  float3 violet = float3(0.62, 0.48, 1.00);
  float3 rose = float3(1.00, 0.52, 0.80);
  float xn = p.x / w;
  float glow = 0.0; float inner = 0.0; float gon = 0.0;
  if (abs(xn) < 1.12) {
    float s = clamp(1.0 - xn * xn, 0.0, 1.0);
    // outer surface: a soft dome, a little flattened on top
    float top = -hgt * pow(s, 0.62);
    // inner (subumbrella) surface: concave, rising into the bell
    float bot = -hgt * 0.24 * pow(s, 0.9) + R * 0.05 * (1.0 - s);
    // margin ruffle
    float ruff = R * 0.018 * sin(xn * 26.0 + t * 1.6) * (1.0 - s);
    bot += ruff;
    float th = max(bot - top, 1.0);
    float k = (p.y - top) / th;                         // 0 at outer skin, 1 at inner
    float inBell = smoothstep(-0.06, 0.02, k) * smoothstep(1.06, 0.98, k);
    // translucent: bright at the skins, faint in the jelly, brighter towards the margin
    float skin = exp(-pow(k / 0.07, 2.0)) * 0.55 + exp(-pow((k - 1.0) / 0.10, 2.0)) * 0.25;
    float body = inBell * (0.10 + 0.12 * (1.0 - s) + 0.10 * exp(-pow((k - 0.35) / 0.4, 2.0)));
    float rimEdge = exp(-pow((abs(xn) - 1.0) / 0.05, 2.0)) * smoothstep(-hgt * 0.25, -hgt * 0.02, p.y) * smoothstep(R * 0.10, 0.0, p.y) * 0.5;
    glow = (skin + body) * smoothstep(1.12, 0.98, abs(xn)) + rimEdge;
    // a ring of tiny lights along the margin, twinkling one after another
    float spots = 0.0;
    if (abs(p.y) < R * 0.25)
    for (int m = 0; m < 16; m++) {
      float fm = float(m);
      float mx = (fm + 0.5) / 16.0 * 2.0 - 1.0;
      float ms = 1.0 - mx * mx;
      float2 mp = float2(mx * w, -hgt * 0.24 * pow(ms, 0.9) + R * 0.05 * (1.0 - ms) + R * 0.012);
      float md = length(p - mp);
      float tw = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(t * 0.7 - fm * 0.8), 4.0);
      spots += exp(-md * md / (R * R * 0.00045)) * tw;
    }
    glow += spots * 0.9;
    // radial canals, faint
    inner = inBell * pow(0.5 + 0.5 * cos(xn * 3.14159 * 7.0), 6.0) * 0.08 * (1.0 - k);
    // four soft gonads seen through the bell
    for (int j = 0; j < 4; j++) {
      float gx = (float(j) - 1.5) * 0.36;
      float2 gd = float2((xn - gx) / 0.17, (p.y + hgt * 0.5) / (hgt * 0.20));
      gon += exp(-dot(gd, gd)) * 0.20;
    }
    gon *= inBell;
  }
  col += cyan * glow + violet * inner + rose * gon;
  // halo
  float hd = length(float2(p.x / (w * 1.6), (p.y + hgt * 0.4) / (hgt * 1.9)));
  col += float3(0.20, 0.45, 0.85) * 0.07 * exp(-hd * hd * 1.4) * (0.8 + 0.4 * pulse);
  // tentacles: many fine threads, waves travelling down, trailing more when the bell contracts
  float tent = 0.0;
  float len = R * 3.6;
  if (p.y > 0.0 && p.y < len + R * 0.1 && abs(p.x) < R * 2.0)
  for (int i = 0; i < 22; i++) {
    float fi = float(i);
    float hi = hash(float2(fi, 4.0));
    float x0 = ((fi + 0.5 * (hi - 0.5)) / 21.0 * 2.0 - 1.0) * w * 0.96;
    float yy = p.y - R * 0.03 - R * 0.03 * hi;
    float li = len * (0.55 + 0.45 * hi);
    if (yy > 0.0 && yy < li) {
      float k = yy / li;
      float wave = sin(yy / R * (2.4 + hi) - t * (0.9 + 0.3 * hi) + fi * 1.9);
      float sway = wave * R * 0.13 * k + sin(t * 0.21 + fi * 0.7) * R * 0.07 * k * k;
      float x = x0 * (1.0 + 0.18 * k - 0.12 * pulse * k) + sway + sign(x0) * R * 0.05 * sin(min(k * 6.0, 3.14159));
      float d = abs(p.x - x);
      float wd = u * 0.0014 * (1.0 - 0.5 * k);
      float fade = pow(1.0 - k, 1.6) * smoothstep(0.0, R * 0.22, yy);
      tent += (exp(-d * d / (wd * wd)) * 0.55 + exp(-d * d / (wd * wd * 36.0)) * 0.05) * fade;
      // tiny beads of light along the thread
      float bead = exp(-pow(fract(yy / (R * 0.18) + hi) - 0.5, 2.0) * 60.0);
      tent += bead * exp(-d * d / (wd * wd * 4.0)) * 0.25 * fade;
    }
  }
  col += float3(0.62, 0.85, 1.0) * tent;
  // oral arms: four long, translucent, frilled ribbons
  float arms = 0.0;
  float alen = R * 2.0;
  if (abs(p.x) < R * 1.0)
  for (int j = 0; j < 4; j++) {
    float fj = float(j);
    float yy = p.y + hgt * 0.30;
    if (yy > 0.0 && yy < alen) {
      float k = yy / alen;
      float x = (fj - 1.5) * R * 0.07 * (1.0 + 1.5 * k) + sin(yy / R * 1.8 - t * 0.6 + fj * 1.7) * R * 0.15 * k;
      float frill = 1.0 + 0.5 * sin(yy / R * 22.0 + fj * 3.0 - t * 1.2);
      float wd = R * (0.045 * (1.0 - k) + 0.008) * frill;
      float d = abs(p.x - x);
      float a0 = smoothstep(0.0, R * 0.10, yy);
      arms += a0 * exp(-d * d / (wd * wd)) * pow(1.0 - k, 1.3) * 0.22;
      arms += a0 * exp(-pow((d - wd) / (R * 0.006), 2.0)) * pow(1.0 - k, 1.5) * 0.18;
    }
  }
  col += mix(violet, rose, 0.4) * arms;
  // vignette and a breath of grain
  float2 vc = frag / res - 0.5;
  col *= 1.0 - dot(vc, vc) * 1.1;
  col += (hash(frag + fract(t * 7.0)) - 0.5) * 0.006;
  return half4(half3(pow(max(col, 0.0), float3(0.90))), 1.0);
}
"""
}
