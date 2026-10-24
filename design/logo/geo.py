import math
PHI = (1 + 5 ** .5) / 2

def f(x):
    s = f"{x:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def tri_pts(H):
    """Sharp play triangle: left edge height H at x=0, apex at x = H*phi/2 (each half is a golden rectangle)."""
    W = H * PHI / 2
    return [(0, -H / 2), (W, 0), (0, H / 2)], W

def fillets(P, r):
    out = []
    n = len(P)
    for i in range(n):
        p, a, b = P[i], P[i - 1], P[(i + 1) % n]
        va = (a[0] - p[0], a[1] - p[1]); vb = (b[0] - p[0], b[1] - p[1])
        la, lb = math.hypot(*va), math.hypot(*vb)
        ua = (va[0] / la, va[1] / la); ub = (vb[0] / lb, vb[1] / lb)
        ang = math.acos(ua[0] * ub[0] + ua[1] * ub[1])
        t = r / math.tan(ang / 2)
        bis = (ua[0] + ub[0], ua[1] + ub[1]); bl = math.hypot(*bis); bis = (bis[0] / bl, bis[1] / bl)
        dc = r / math.sin(ang / 2)
        c = (p[0] + bis[0] * dc, p[1] + bis[1] * dc)
        out.append(dict(s=(p[0] + ua[0] * t, p[1] + ua[1] * t), e=(p[0] + ub[0] * t, p[1] + ub[1] * t), c=c))
    return out

def rtri(H, r):
    P, W = tri_pts(H)
    F = fillets(P, r)
    d = f"M{f(F[0]['e'][0])},{f(F[0]['e'][1])}"
    for i in range(1, 4):
        k = F[i % 3]
        d += f" L{f(k['s'][0])},{f(k['s'][1])} A{f(r)},{f(r)} 0 0 1 {f(k['e'][0])},{f(k['e'][1])}"
    return d + " Z", W

def outline(H, r, steps=24):
    """Polygon approximation of the rounded triangle (for bbox / centroid)."""
    P, W = tri_pts(H)
    F = fillets(P, r)
    pts = []
    for k in F:
        a0 = math.atan2(k['s'][1] - k['c'][1], k['s'][0] - k['c'][0])
        a1 = math.atan2(k['e'][1] - k['c'][1], k['e'][0] - k['c'][0])
        while a1 < a0: a1 += 2 * math.pi
        for j in range(steps + 1):
            a = a0 + (a1 - a0) * j / steps
            pts.append((k['c'][0] + r * math.cos(a), k['c'][1] + r * math.sin(a)))
    return pts

def inside(pts, x, y):
    c = False
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]; x2, y2 = pts[i - 1]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c
