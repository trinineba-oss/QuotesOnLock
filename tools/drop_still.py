"""Renders the still pictures for the "A single drop" theme with the same maths as DropArt.SHADER:
drop_still.jpg (the full screen, shown when the theme can't move) and drop_frame.jpg (the card and widget
frame). Usage: drop_still.py <tau seconds after impact> <out dir>"""
import sys, math, numpy as np
from PIL import Image
VH, VI = 0.40, 0.60
def h1(i, k): return np.modf(np.sin(i * 1.37 + k * 0.71 + 0.3) * 43758.5453)[0] % 1.0
def col(c): return np.array(c, np.float32)
def backdrop(px, py, asp):
    t = np.clip(py / VH, 0, 1)[..., None]
    c = col([0.0235, 0.0706, 0.1098]) * (1 - t) + col([0.0706, 0.2039, 0.2667]) * t
    for i in range(16):
        bx = h1(i, 1) * 1.2 - 0.1; by = h1(i, 2) * 0.45 - 0.05; br = 0.03 + 0.06 * h1(i, 3); k = h1(i, 4)
        bc = [0.949, 0.714, 0.420] if k < 0.25 else [0.910, 0.604, 0.353] if k < 0.5 else [0.624, 0.839, 0.878] if k < 0.75 else [1.0, 0.886, 0.659]
        ba = 0.25 + 0.45 * h1(i, 5)
        d = np.sqrt(((px - bx) * asp) ** 2 + (py - by) ** 2) / br
        c = c + col(bc) * (ba * np.clip(1.2 - d, 0, 1) ** 1.5 * 0.55)[..., None]
    return c
def packet(R, tau, c, lam, A, dl):
    ph = R - c * max(tau - dl, 0.0)
    env = np.exp(-(ph / 0.35) ** 2) * (ph <= 0.25)
    return A * env * np.cos(6.2831853 * ph / lam) * math.exp(-tau / 3.2) / np.sqrt(1 + 4 * R)
def height_one(u, v, tau):
    if tau < 0: return np.zeros_like(u)
    dz = 1 / np.maximum(v - VH, 0.001); X = (u - 0.5) * dz * 0.9; Z0 = 1 / (VI - VH)
    R = np.sqrt(X * X + (dz - Z0) ** 2)
    h = packet(R, tau, 0.55, 0.22, 0.030, 0) + packet(R, tau, 0.42, 0.16, 0.018, 0.10) + packet(R, tau, 0.30, 0.12, 0.010, 0.22)
    return h - 0.05 * math.exp(-tau / 0.18) * np.exp(-(R / 0.12) ** 2)
def height(u, v, tau): return height_one(u, v, tau) + height_one(u, v, tau + 5.0)
def render(W, H, tau):
    v, u = np.mgrid[0:H, 0:W].astype(np.float32); u = (u + 0.5) / W; v = (v + 0.5) / H; asp = W / H
    def sphere(img, cx, cy, r, stretch=1.0, alpha=1.0):
        dx = (u - cx) * asp; dy = (v - cy) / stretch; d = np.sqrt(dx * dx + dy * dy) / r
        m = d < 1
        if not m.any(): return img
        n = np.sqrt(np.clip(1 - d * d, 0, 1))
        inside = backdrop(cx - (u - cx) * 0.35, np.clip(cy - (v - cy) * 0.35 + 0.08, 0, 0.39), asp)
        rim = (1 - n) ** 1.5
        c = inside * (0.55 + 0.6 * n)[..., None] * (1 - 0.75 * rim)[..., None]
        c = c + (np.exp(-((dx / r + 0.35) ** 2 + (dy / r + 0.45) ** 2) / 0.03) * 1.2)[..., None] + col([0.949, 0.714, 0.420]) * (np.exp(-((dx / r - 0.3) ** 2 + (dy / r - 0.55) ** 2) / 0.05) * 0.35)[..., None]
        a = (np.clip((1 - d) * r * H / 1.5, 0, 1) * alpha)[..., None]
        return np.where(m[..., None], img * (1 - a) + c * a, img)
    du, dv = 1 / 360, 1 / 780
    gx = (height(u + du, v, tau) - height(u - du, v, tau)) * 0.5
    gy = (height(u, v + dv, tau) - height(u, v - dv, tau)) * 0.5
    ry = np.clip((VH - (v - VH) * 0.85) + gy * 55, -0.2, VH)
    fres = np.clip(0.25 + 0.75 * np.exp(-(v - VH) * 3), 0, 1)[..., None]
    wc = col([0.0157, 0.0627, 0.0863]) * (1 - fres) + backdrop(u + gx * 33, ry, asp) * fres
    wc = wc + col([1.0, 0.945, 0.847]) * (np.maximum(-gy * 120 + gx * 40, 0) ** 2.2 * 0.9)[..., None]
    img = np.where((v > VH)[..., None], wc, backdrop(u, v, asp))
    tfall = tau + 0.9
    if 0 <= tfall < 0.9:
        p = tfall / 0.9; cy = -0.05 + (VI - 0.012 + 0.05) * p * p
        img = sphere(img, 0.5, cy, 0.022, 1 + 0.25 * p)
        ry2 = VH + (VH - cy) * 0.85
        if ry2 > VH: img = sphere(img, 0.5, ry2, 0.020, 1.0, 0.25 * p)
    if 0.05 < tau < 1.0:
        q = (tau - 0.05) / 0.95; hgt = 0.085 * max(math.sin(math.pi * q), 0) ** 0.8
        if hgt > 0.004:
            rel = np.clip((VI - v) / hgt, 0, 1); wdt = 0.016 - 0.006 * rel + 0.010 * np.exp(-(VI - v) / 0.012)
            dx = (u - 0.5) * asp; m = (v < VI + 0.004) & (v > VI - hgt) & (np.abs(dx) < wdt)
            s = dx / np.maximum(wdt, 1e-4); n = np.sqrt(np.clip(1 - s * s, 0, 1))
            inside = backdrop(0.5 - (u - 0.5) * 0.5, np.clip(0.30 - (VI - v) * 0.6, 0, 0.39), asp)
            c = inside * (0.6 + 0.7 * n)[..., None] * (1 - 0.7 * (1 - n) ** 1.4)[..., None]
            c = c + col([1.0, 0.965, 0.902]) * (np.exp(-(s + 0.4) ** 2 / 0.015) * 0.9)[..., None] + col([0.949, 0.714, 0.420]) * (np.exp(-(s - 0.55) ** 2 / 0.03) * 0.25)[..., None]
            a2 = np.clip((wdt - np.abs(dx)) * 780 / 1.2, 0, 1)[..., None]
            img = np.where(m[..., None], img * (1 - a2) + c * a2, img)
            img = sphere(img, 0.5, VI - hgt, 0.0105)
    if 0.42 < tau < 1.55:
        s2 = (tau - 0.42) / 1.13; y0 = VI - 0.085 * math.sin(math.pi * (0.37 / 0.95)) ** 0.8 - 0.012
        y = y0 - 0.05 * math.sin(math.pi * min(s2 * 1.6, 1)) if s2 < 0.625 else y0 + ((s2 - 0.625) / 0.375) ** 2 * (VI - y0)
        img = sphere(img, 0.5, y, 0.0085, 1.2 if s2 > 0.625 else 1.0)
    return Image.fromarray((np.clip(img, 0, 1) ** (1 / 1.15) * 255).astype(np.uint8))
if __name__ == '__main__':
    tau = float(sys.argv[1]); out = sys.argv[2]
    full = render(720, 1560, tau)
    full.save(out + '/drop_still.jpg', quality=88)
    W, Hh = 720, 1560
    full.crop((0, 690, W, 990)).save(out + '/drop_frame.jpg', quality=88)
