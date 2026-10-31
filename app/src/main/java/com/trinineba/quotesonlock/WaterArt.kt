package com.trinineba.quotesonlock

/**
 * Calm water at sunrise, drawn per pixel on the GPU (Android 13+): a camera looks down at a water
 * plane whose surface is the sum of 32 small waves; each point reflects a sunrise sky (more so
 * toward the distance) and glints where it catches the low sun. Generated together with the still
 * images water.jpg and water_frame.jpg so all three match. Every wave repeats in [LOOP_SECONDS].
 */
internal object WaterArt {
    const val LOOP_SECONDS = 8.0f

    const val SHADER = """
uniform float2 res;
uniform float time;

void acc(float2 k, float w, float a, float ph, float lam, float X, float Z, float foot, float tm, inout float2 g) {
    float fade = clamp(lam / (4.0 * foot) - 1.0, 0.0, 1.0);
    float c = cos(k.x * X + k.y * Z - w * tm + ph) * a * fade;
    g += k * c;
}

float3 sky(float3 r) {
    float e = clamp(r.y, 0.0, 1.0);
    if (e < 0.22) return mix(float3(1.000000, 0.768627, 0.541176), float3(0.886275, 0.588235, 0.627451), e / 0.22);
    return mix(float3(0.886275, 0.588235, 0.627451), float3(0.360784, 0.470588, 0.666667), clamp((e - 0.22) / 0.6, 0.0, 1.0));
}

half4 main(float2 p) {
    float tf = 0.554309;
    float u = (p.x - res.x * 0.5) / (res.y * 0.5) * tf;
    float v = -(p.y - res.y * 0.5) / (res.y * 0.5) * tf;
    float3 d = normalize(float3(u, v, 1.0));
    float dy = d.y * 0.788011 - d.z * 0.615661;
    float dz = d.y * 0.615661 + d.z * 0.788011;
    float3 D = float3(d.x, dy, dz);
    float t = 1.600000 / (-dy);
    float X = t * d.x;
    float Z = t * dz;
    float foot = t * (2.0 * tf / res.y) / max(-dy, 0.05);
    float2 g = float2(0.0);
    acc(float2(64.374280, 60.568550), 29.059732, 0.000655, 0.180258, 0.071086, X, Z, foot, time, g);
    acc(float2(-23.396030, 71.452969), 27.488936, 0.000511, 0.815394, 0.083569, X, Z, foot, time, g);
    acc(float2(-45.418708, 22.638649), 21.991149, 0.000947, 3.213158, 0.123811, X, Z, foot, time, g);
    acc(float2(-3.846667, 46.839927), 21.205750, 0.000845, 4.951399, 0.133692, X, Z, foot, time, g);
    acc(float2(-4.867218, 39.856150), 19.634954, 0.001559, 3.449942, 0.156484, X, Z, foot, time, g);
    acc(float2(-24.904912, 19.415416), 17.278760, 0.001657, 3.038704, 0.198969, X, Z, foot, time, g);
    acc(float2(-15.185805, 28.578267), 18.064158, 0.001289, 5.040388, 0.194151, X, Z, foot, time, g);
    acc(float2(0.524448, 23.853467), 14.922565, 0.002002, 1.741353, 0.263344, X, Z, foot, time, g);
    acc(float2(5.934505, 25.232329), 15.707963, 0.001816, 0.927972, 0.242399, X, Z, foot, time, g);
    acc(float2(10.810265, 14.960486), 13.351769, 0.006450, 1.364383, 0.340414, X, Z, foot, time, g);
    acc(float2(2.860933, 19.120131), 14.137167, 0.004392, 2.946237, 0.324998, X, Z, foot, time, g);
    acc(float2(-6.115230, 11.117100), 10.995574, 0.006136, 0.106043, 0.495206, X, Z, foot, time, g);
    acc(float2(-5.820985, 12.260219), 11.780972, 0.006347, 4.341932, 0.462955, X, Z, foot, time, g);
    acc(float2(2.874668, 11.695029), 10.995574, 0.008801, 3.693771, 0.521723, X, Z, foot, time, g);
    acc(float2(3.353440, 8.864353), 9.424778, 0.006199, 1.084915, 0.662961, X, Z, foot, time, g);
    acc(float2(4.031626, 7.958204), 9.424778, 0.008711, 0.799239, 0.704302, X, Z, foot, time, g);
    acc(float2(3.593662, 4.941682), 7.853982, 0.008104, 3.759808, 1.028310, X, Z, foot, time, g);
    acc(float2(1.459504, 4.794676), 7.068583, 0.015498, 1.194873, 1.253655, X, Z, foot, time, g);
    acc(float2(-0.583000, 5.437261), 7.068583, 0.010553, 4.568578, 1.148993, X, Z, foot, time, g);
    acc(float2(-3.336048, 2.415289), 6.283185, 0.011634, 1.980352, 1.525565, X, Z, foot, time, g);
    acc(float2(-2.154968, 3.123036), 6.283185, 0.019347, 2.140594, 1.655923, X, Z, foot, time, g);
    acc(float2(2.162878, 2.181461), 5.497787, 0.019247, 0.251410, 2.045348, X, Z, foot, time, g);
    acc(float2(0.432523, 2.932597), 5.497787, 0.013940, 5.310705, 2.119603, X, Z, foot, time, g);
    acc(float2(-0.696378, 1.937947), 4.712389, 0.021491, 4.349718, 3.051176, X, Z, foot, time, g);
    acc(float2(-0.754937, 1.572095), 3.926991, 0.015722, 3.934092, 3.602816, X, Z, foot, time, g);
    acc(float2(-0.773293, 1.639672), 3.926991, 0.024125, 5.621552, 3.465873, X, Z, foot, time, g);
    acc(float2(-0.170574, 1.274820), 3.926991, 0.020272, 1.024516, 4.885149, X, Z, foot, time, g);
    acc(float2(-0.117778, 1.025622), 3.141593, 0.019536, 2.244478, 6.086221, X, Z, foot, time, g);
    acc(float2(1.115983, 0.352496), 3.141593, 0.024974, 1.003728, 5.368729, X, Z, foot, time, g);
    acc(float2(0.620972, 0.610010), 3.141593, 0.026176, 1.455583, 7.218156, X, Z, foot, time, g);
    acc(float2(0.302710, 0.809830), 3.141593, 0.025303, 4.002267, 7.267525, X, Z, foot, time, g);
    acc(float2(0.077359, 0.668687), 2.356194, 0.022944, 0.820955, 9.334048, X, Z, foot, time, g);
    float3 n = normalize(float3(-g.x, 1.0, -g.y));
    float dn = dot(D, n);
    float3 r = D - 2.0 * dn * n;
    float fres = 0.02 + 0.98 * pow(1.0 - clamp(-dn, 0.0, 1.0), 5.0);
    float3 col = float3(0.047059, 0.133333, 0.219608) * (1.0 - fres) + sky(r) * fres;
    col += pow(clamp(dot(r, float3(0.118562, 0.098802, 0.988019)), 0.0, 1.0), 180.0) * 1.3 * float3(1.000000, 0.839216, 0.627451);
    float haze = clamp((t - 3.0) / 40.0, 0.0, 0.35);
    col = col * (1.0 - haze) + float3(0.925490, 0.729412, 0.627451) * haze;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
