"""Generates the logo variants (SVG strings) used by the review page and the Android export."""
import sys, json, math
sys.path.insert(0, '.')
from geo import *

H = 110                    # construction height of the front triangle (2 x 55)
def rad(n): return H / PHI ** n   # corner radius family: H/phi^5 = 10, H/phi^4 = 16, H/phi^3 = 26

K = PHI ** -0.2            # per-layer shrink, 0.908 (logo A used 0.98/0.90/0.82, i.e. ~0.91 per step)
ANCHOR = 0.44              # layers shrink about this fraction of W from the left edge, as logo A did

def layers(kind, r, d=26):
    """Returns [(scale, left_x)] back..front, all on one horizontal axis."""
    _, W = tri_pts(H)
    if kind == 'orig':
        # logo A's arrangement: smaller layers behind, each stepped left by d (= r = H/phi^4)
        c = ANCHOR * W
        return [(K ** 2, c * (1 - K ** 2) - 2 * d), (K, c * (1 - K) - d), (1.0, 0.0)]
    if kind == 'nested':
        hs = [1, 1 / PHI, 1 / PHI ** 2]     # 110 / 68 / 42
        cx = W / 3                           # shared centroid
        return [(k, cx - k * W / 3) for k in hs]
    if kind == 'depth':
        S = [1 / PHI, 1 / PHI ** .5, 1]
    else:
        S = [1, 1, 1]
    bands = (26, 16)                         # how far each echo's apex runs past the layer in front (2x13, 2x8)
    sb, sm, sf = S
    Lf = 0.0
    Lm = sf * W + bands[0] - sm * W
    Lb = Lm + sm * W + bands[1] - sb * W
    return [(sb, Lb), (sm, Lm), (sf, Lf)]

def geometry(kind, r, d=26):
    pts = outline(H, r)
    L = layers(kind, r, d)
    polys = [[(x * s + lx, y * s) for x, y in pts] for s, lx in L]
    xs = [p[0] for pl in polys for p in pl]; ys = [p[1] for pl in polys for p in pl]
    bb = (min(xs), min(ys), max(xs), max(ys))
    # area centroid of the union, by sampling
    sx = sy = n = 0
    N = 120
    for i in range(N):
        for j in range(N):
            x = bb[0] + (bb[2] - bb[0]) * (i + .5) / N
            y = bb[1] + (bb[3] - bb[1]) * (j + .5) / N
            if any(inside(pl, x, y) for pl in polys):
                sx += x; sy += y; n += 1
    cen = (sx / n, sy / n)
    return L, bb, cen

def mark(kind, r, fills, gap, gapcol, cx, cy, height, step=26):
    """Place the mark so its visible height = height, centred halfway between bbox centre and area centroid."""
    d, W = rtri(H, r)
    L, bb, cen = geometry(kind, r, step)
    sc = height / (bb[3] - bb[1])
    ox_mid = ((bb[0] + bb[2]) / 2 + cen[0]) / 2
    tx = cx - ox_mid * sc
    ty = cy - (bb[1] + bb[3]) / 2 * sc
    g = [f'<g transform="translate({f(tx)},{f(ty)}) scale({f(sc, )})">']
    for i, (s, lx) in enumerate(L):
        st = ''
        if gap and i > 0:
            st = f' stroke="{gapcol}" stroke-width="{f(gap / s / sc)}" paint-order="stroke" stroke-linejoin="round"'
        g.append(f'<path d="{d}" transform="translate({f(lx)},0) scale({f(s)})" fill="{fills[i]}"{st}/>')
    g.append('</g>')
    w = (bb[2] - bb[0]) * sc
    return ''.join(g), w, (cx - ox_mid * sc + bb[0] * sc)

GRAD_C = ('<linearGradient id="gc0" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1fd1b2"/><stop offset="1" stop-color="#0fa896"/></linearGradient>'
          '<linearGradient id="gc1" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8a63ff"/><stop offset="1" stop-color="#6a3df0"/></linearGradient>'
          '<linearGradient id="gc2" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5ab8ff"/><stop offset="1" stop-color="#1f6fff"/></linearGradient>'
          '<linearGradient id="gcb" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#141b28"/><stop offset="1" stop-color="#0b0f18"/></linearGradient>')

V = {
  'blue':  dict(title='φ 蓝阶', kind='orig', bg='#1d1d1f', gapcol='#1d1d1f', fills=['#0b3f80', '#0066cc', '#2997ff'], word=['#f5f5f7', '#2997ff']),
  'glass': dict(title='φ 蓝底', kind='orig', bg='#0071e3', gapcol='#0071e3', fills=['rgba(255,255,255,.382)', 'rgba(255,255,255,.618)', '#ffffff'], word=['#ffffff', 'rgba(255,255,255,.75)']),
  'tri':   dict(title='三色柔化', kind='orig', bg='url(#gcb)', gapcol='#10151f', fills=['url(#gc0)', 'url(#gc1)', 'url(#gc2)'], word=['#f5f5f7', '#2997ff']),
}

FONT = "'Noto Sans SC','PingFang SC','Source Han Sans SC',sans-serif"

def icon(k, r=None, d=26, size=240, rx=54):
    v = V[k]; r = rad(4) if r is None else r
    # visible mark height = tile / phi^2 (240 -> 92)
    m, _, _ = mark(v['kind'], r, v['fills'], 4, v['gapcol'], 120, 120, 240 / PHI ** 2, step=d)
    return (f'<svg viewBox="0 0 240 240" xmlns="http://www.w3.org/2000/svg"><defs>{GRAD_C}</defs>'
            f'<rect width="240" height="240" rx="{rx}" fill="{v["bg"]}"/>{m}</svg>')

def banner(k, r=None, d=26, font=FONT):
    """640x360 (xhdpi 320x180dp). Type 84, weight 600, all white; visible mark height 136 = 84*phi; gap 52 = 84/phi."""
    v = V[k]; r = rad(4) if r is None else r
    fs, mh, gap = 84, 136, 52
    _, w, _ = mark(v['kind'], r, v['fills'], 4, v['gapcol'], 0, 0, mh, step=d)
    total = w + gap + fs * 2
    x0 = (640 - total) / 2
    # mark() centres optically; shift so its bbox starts at x0
    m, w, left = mark(v['kind'], r, v['fills'], 4, v['gapcol'], 0, 180, mh, step=d)
    m, w, left2 = mark(v['kind'], r, v['fills'], 4, v['gapcol'], x0 - left, 180, mh, step=d)
    tx = x0 + w + gap
    return (f'<svg viewBox="0 0 640 360" xmlns="http://www.w3.org/2000/svg"><defs>{GRAD_C}</defs>'
            f'<rect width="640" height="360" fill="{v["bg"]}"/>{m}'
            f'<text x="{f(tx)}" y="180" dy=".36em" font-family="{font}" font-weight="600" font-size="{fs}" fill="{v["word"][0]}">直播</text></svg>')

if __name__ == '__main__':
    out = {k: dict(title=V[k]['title'], icon=icon(k), banner=banner(k)) for k in V}
    out['_round'] = {str(n): icon('blue', rad(n)) for n in (5, 4, 3)}
    out['_step'] = {str(d): icon('blue', d=d) for d in (16, 26, 42)}
    json.dump(out, open('variants.json', 'w'), ensure_ascii=False)
    html = '<html><body style="margin:0;background:#888;display:grid;grid-template-columns:240px 427px;gap:12px;padding:12px">'
    for k in V:
        html += f'<div style="width:240px">{out[k]["icon"]}</div><div style="width:427px">{out[k]["banner"]}</div>'
    html += ''.join(f'<div style="width:240px">{s}</div>' for s in list(out['_round'].values())+list(out['_step'].values()))
    open('preview.html', 'w').write(html + '</body></html>')
    print({n: round(rad(n), 2) for n in (5, 4, 3)})
