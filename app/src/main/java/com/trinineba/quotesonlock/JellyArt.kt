package com.trinineba.quotesonlock

/**
 * The deep (Android 13+, drawn per pixel on the GPU): one jellyfish alone in dark water, moving slowly and majestically,
 * modelled on footage of a real one. Its bell beats about every four seconds, drawing up from a wide, flat saucer into a
 * tall, round dome, holding a moment, and opening slowly again. On a 100-second cycle it floats almost still, then rises
 * slowly with fuller beats, floats again, and sinks gently back. Its long, fine tentacles hang from where the bell was a
 * moment ago, so they trail its path, and each beat sends a slow ripple down them. A ring of tiny lights twinkles along
 * its margin, a smaller one drifts far off in the deep, and sparse marine snow falls past. Sized from the shorter side,
 * so in landscape it is a small, distant light in a wide dark.
 * tools/jelly_still/jelly.frag is the same maths in WebGL, used to render jelly_still.jpg and jelly_frame.jpg.
 */
internal object JellyArt {
    const val SHADER = """
uniform float2 res;
uniform float time;

float hash(float2 p){ return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

// ---- how it swims -------------------------------------------------------------------------------
// Slow and majestic, on a 100-second cycle. It floats, hanging almost still with slow, soft beats (0-30 s);
// then it moves, rising slowly with fuller beats, each one lifting it a little (30-65 s); it floats again up
// there (65-80 s), and finally lets itself sink gently back down to where it began (80-100 s).
const float CYCLE = 100.0;
const float BEAT = 4.2;

float ease(float x) { x = clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

// height gained within one beat while moving: a soft push as it squeezes, then a slow coast
float lift(float ph) {
  float x = max(ph - 0.1, 0.0);
  return (1.0 - exp(-x * 4.0)) / (1.0 - exp(-0.9 * 4.0));
}

float2 place(float t) {
  float tc = mod(t, CYCLE);
  float x = 0.5 + 0.07 * sin(t * 0.031 + 1.0) + 0.02 * sin(t * 0.083);
  float y0 = 0.58, y1 = 0.38;
  float y;
  if (tc < 30.0) {
    y = y0 + 0.008 * sin(tc / BEAT * 6.2832 - 1.0);                 // floating: barely bobbing
  } else if (tc < 65.0) {
    float m = tc - 30.0;
    float n = floor(m / BEAT), ph = fract(m / BEAT);
    float beats = 35.0 / BEAT;
    float k = clamp((n + lift(ph)) / beats, 0.0, 1.0);
    y = mix(y0, y1, ease(k));
  } else if (tc < 80.0) {
    y = y1 + 0.008 * sin(tc / BEAT * 6.2832 - 1.0);
  } else {
    y = mix(y1, y0, ease((tc - 80.0) / 20.0));
  }
  return float2(x, y);
}

// How hard the bell is squeezed (0 open, 1 fully contracted): slow beats, softest while it floats or sinks.
float squeeze(float t) {
  float tc = mod(t, CYCLE);
  float ph = fract(t / BEAT);
  float p = smoothstep(0.0, 0.42, ph) * (1.0 - smoothstep(0.55, 1.0, ph));
  float moving = smoothstep(28.0, 32.0, tc) * (1.0 - smoothstep(63.0, 67.0, tc));
  float sinking = smoothstep(80.0, 84.0, tc) * (1.0 - smoothstep(96.0, 100.0, tc));
  return p * mix(0.6, 1.0, moving) * mix(1.0, 0.45, sinking);
}

// ---- one jellyfish --------------------------------------------------------------------------------
float3 jelly(float2 frag, float2 c, float ang, float pulse, float R, float u, float t, float turn, float speed, float2 lag, float later) {
  float3 col = float3(0.0);
  float ca = cos(ang), sa = sin(ang);
  float2 p = frag - c;
  p = float2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
  float w = R * (1.0 - 0.30 * pulse);
  float hgt = R * 0.56 * (1.0 + 0.80 * pulse);
  float3 cyan = float3(0.42, 0.80, 1.00);
  float3 violet = float3(0.62, 0.48, 1.00);
  float3 rose = float3(1.00, 0.52, 0.80);
  float xn = p.x / w;
  float glow = 0.0; float inner = 0.0; float gon = 0.0;
  if (abs(xn) < 1.12 && p.y > -hgt * 1.2 && p.y < R * 0.3) {
    float s = clamp(1.0 - xn * xn, 0.0, 1.0);
    float top = -hgt * pow(s, mix(0.62, 0.36, pulse));
    float bot = -hgt * 0.24 * pow(s, 0.9) + R * 0.05 * (1.0 - s);
    // the margin ripples, and flares out as the bell opens again
    bot += R * 0.018 * sin(xn * 26.0 + t * 0.8) * (1.0 - s) + R * 0.05 * (1.0 - pulse) * (1.0 - s) * (1.0 - s);
    float th = max(bot - top, 1.0);
    float k = (p.y - top) / th;
    float inBell = smoothstep(-0.06, 0.02, k) * smoothstep(1.06, 0.98, k);
    float skin = exp(-pow(k / 0.07, 2.0)) * 0.55 + exp(-pow((k - 1.0) / 0.10, 2.0)) * 0.25;
    float body = inBell * (0.10 + 0.12 * (1.0 - s) + 0.10 * exp(-pow((k - 0.35) / 0.4, 2.0)));
    float rimEdge = exp(-pow((abs(xn) - 1.0) / 0.05, 2.0)) * smoothstep(-hgt * 0.25, -hgt * 0.02, p.y) * smoothstep(R * 0.10, 0.0, p.y) * 0.5;
    glow = (skin + body) * smoothstep(1.12, 0.98, abs(xn)) + rimEdge;
    // the ring of lights flares as the bell contracts, a ripple of light around the margin
    float spots = 0.0;
    if (abs(p.y) < R * 0.25)
    for (int m = 0; m < 16; m++) {
      float fm = float(m);
      float mx = (fm + 0.5) / 16.0 * 2.0 - 1.0;
      float ms = 1.0 - mx * mx;
      float2 mp = float2(mx * w, -hgt * 0.24 * pow(ms, 0.9) + R * 0.05 * (1.0 - ms) + R * 0.012);
      float md = length(p - mp);
      float tw = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(t * 0.4 - fm * 0.8), 4.0);
      tw += 0.8 * pulse * exp(-pow(abs(mx) - pulse, 2.0) * 8.0);
      spots += exp(-md * md / (R * R * 0.00045)) * tw;
    }
    glow += spots * 0.9;
    inner = inBell * pow(0.5 + 0.5 * cos(xn * 3.14159 * 7.0), 6.0) * 0.08 * (1.0 - k);
    for (int j = 0; j < 4; j++) {
      float gx = (float(j) - 1.5) * 0.36;
      float2 gd = float2((xn - gx) / 0.17, (p.y + hgt * 0.5) / (hgt * 0.20));
      gon += exp(-dot(gd, gd)) * 0.20;
    }
    gon *= inBell;
    glow *= 1.0 + 0.35 * pulse;     // a little brighter as it works
  }
  col += cyan * glow + violet * inner + rose * gon;
  float hd = length(float2(p.x / (w * 1.6), (p.y + hgt * 0.4) / (hgt * 1.9)));
  col += float3(0.20, 0.45, 0.85) * 0.07 * exp(-hd * hd * 1.4) * (0.8 + 0.6 * pulse);
  // tentacles: long and fine; each point hangs from where the bell was a moment ago, so they trail its path,
  // and every squeeze sends a ripple down them, drawing them in and letting them float out again
  float tent = 0.0;
  float len = R * 5.2;
  float2 lq = float2(ca * lag.x + sa * lag.y, -sa * lag.x + ca * lag.y);
  float py = p.y - lq.y;
  if (py > 0.0 && py < len + R * 0.1 && abs(p.x - lq.x) < R * 2.4)
  for (int i = 0; i < 12; i++) {
    float fi = float(i);
    float hi = hash(float2(fi, 4.0));
    float x0 = ((fi + 0.5 * (hi - 0.5)) / 11.0 * 2.0 - 1.0) * w * 0.85;
    float yy = py - R * 0.03 - R * 0.03 * hi;
    float li = len * (0.5 + 0.5 * hi);
    if (yy > 0.0 && yy < li) {
      float k = yy / li;
      float wave = sin(yy / R * (1.6 + 0.6 * hi) - t * (0.38 + 0.14 * hi) + fi * 1.9);
      float sway = wave * R * 0.07 * k + sin(t * 0.10 + fi * 0.7) * R * 0.05 * k * k;
      float inward = 1.0 - 0.35 * later * (1.0 - k * 0.5);
      float x = x0 * (1.0 + 0.25 * k) * inward + sway + lq.x + sign(x0) * R * 0.04 * sin(min(k * 6.0, 3.14159));
      float d = abs(p.x - x);
      float wd = u * 0.0012 * (1.0 - 0.55 * k);
      float fade = pow(1.0 - k, 1.2) * smoothstep(0.0, R * 0.22, yy);
      tent += (exp(-d * d / (wd * wd)) * 0.6 + exp(-d * d / (wd * wd * 36.0)) * 0.05) * fade;
      float bead = exp(-pow(fract(yy / (R * 0.2) + hi) - 0.5, 2.0) * 60.0);
      tent += bead * exp(-d * d / (wd * wd * 4.0)) * 0.22 * fade;
    }
  }
  col += float3(0.62, 0.85, 1.0) * tent;
  float arms = 0.0;
  float alen = R * 2.0;
  if (abs(p.x) < R * 1.3)
  for (int j = 0; j < 4; j++) {
    float fj = float(j);
    float yy = p.y - lq.y * 0.6 + hgt * 0.30;
    if (yy > 0.0 && yy < alen) {
      float k = yy / alen;
      float x = (fj - 1.5) * R * 0.07 * (1.0 + 1.5 * k) + sin(yy / R * 1.8 - t * 0.33 + fj * 1.7) * R * 0.15 * k * (1.0 - 0.5 * speed) + lq.x * 0.6;
      float frill = 1.0 + 0.5 * sin(yy / R * 22.0 + fj * 3.0 - t * 0.6);
      float wd = R * (0.045 * (1.0 - k) + 0.008) * frill;
      float d = abs(p.x - x);
      float a0 = smoothstep(0.0, R * 0.10, yy);
      arms += a0 * exp(-d * d / (wd * wd)) * pow(1.0 - k, 1.3) * 0.22;
      arms += a0 * exp(-pow((d - wd) / (R * 0.006), 2.0)) * pow(1.0 - k, 1.5) * 0.18;
    }
  }
  col += mix(violet, rose, 0.4) * arms;
  return col;
}

half4 main(float2 frag) {
  float u = min(res.x, res.y * 0.56);
  float t = time;
  float vy = frag.y / res.y;
  float3 col = mix(float3(0.012, 0.045, 0.090), float3(0.001, 0.004, 0.012), smoothstep(0.0, 0.95, vy));
  float rx = frag.x / res.x - 0.5;
  float shaft = exp(-pow((rx - 0.18 * vy + 0.04 * sin(t * 0.05)) * 4.0, 2.0)) * pow(1.0 - vy, 2.0);
  col += float3(0.20, 0.40, 0.55) * shaft * 0.05;

  // where it is now, and (for the tentacles) where it was when the bell passed the point beside this pixel
  float2 here = place(t);
  float2 before = place(t - 0.6);
  float2 v = (here - before) * res / 0.6;
  float sp = length(v) / u;
  float speed = clamp(sp * 8.0, 0.0, 1.0);
  float lean = clamp(atan(v.x, -v.y), -0.5, 0.5) * smoothstep(0.01, 0.06, sp) * 0.5;
  float ang = -lean + 0.05 * sin(t * 0.12) + 0.025 * sin(t * 0.29);
  float pulse = squeeze(t);
  float below = max(frag.y - here.y * res.y, 0.0);
  float delay = below / (u * 0.22 * 0.7);          // seconds for the bell's motion to reach this far down
  float2 lag = (place(t - delay) - here) * res;
  float later = squeeze(t - delay * 0.8);
  float turn = 0.0;

  // marine snow; it streams past a little as the jellyfish surges
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

  // a second, far smaller one deep in the distance, on its own slow rhythm
  float2 farC = float2(res.x * (0.80 + 0.08 * sin(t * 0.021)), res.y * (0.72 - 0.10 * fract(t / 90.0)) );
  float farPulse = squeeze(t * 0.83 + 17.0);
  col += jelly(frag, farC, 0.15 * sin(t * 0.05), farPulse, u * 0.06, u, t * 0.9 + 5.0, 0.0, 0.2, float2(0.0), farPulse) * 0.35 * sin(3.14159 * fract(t / 90.0));

  col += jelly(frag, here * res, ang, pulse, u * 0.22, u, t, turn, speed, lag, later);

  float2 vc = frag / res - 0.5;
  col *= 1.0 - dot(vc, vc) * 1.1;
  col += (hash(frag + fract(t * 7.0)) - 0.5) * 0.006;
  return half4(half3(pow(max(col, 0.0), float3(0.90))), 1.0);
}
"""
}
