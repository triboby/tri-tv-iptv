"""Writes the chosen logo (三色柔化, r = H/phi^4, step 16) into the Android res tree and docs/."""
import sys, os, json
sys.path.insert(0, '.')
from build import *

REPO = sys.argv[1]
R, D = rad(4), 16
GRADS = [('#FF1FD1B2', '#FF0FA896'), ('#FF8A63FF', '#FF6A3DF0'), ('#FF5AB8FF', '#FF1F6FFF')]  # teal, violet, blue
BG = '#FF10151F'   # gap colour: middle of the background gradient
BX, BY = 74.514, 42.26  # path bbox: x 0..BX, y -BY..BY

def place(height, cx, cy):
    L, bb, cen = geometry('orig', R, D)
    sc = height / (bb[3] - bb[1])
    ox_mid = ((bb[0] + bb[2]) / 2 + cen[0]) / 2
    return L, sc, cx - ox_mid * sc, cy - (bb[1] + bb[3]) / 2 * sc, bb, ox_mid

# Adaptive foreground: 108 viewport, 72 visible; mark height 72/phi^2 like the 240 tile's 92.
path, _ = rtri(H, R)
mh = 72 / PHI ** 2
L, sc, tx, ty, bb, ox_mid = place(mh, 54, 54)
gap = 4 * mh / 92
x = ['<?xml version="1.0" encoding="utf-8"?>',
     '<!-- tri.iptv mark: three stacked rounded play triangles (teal, violet, blue), smaller layers behind and to the left.',
     '     Golden-ratio construction: apex at H*phi/2, corner radius H/phi^4, layers shrink by phi^-1/5. -->',
     '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
     '    xmlns:aapt="http://schemas.android.com/aapt"',
     '    android:width="108dp"', '    android:height="108dp"',
     '    android:viewportWidth="108"', '    android:viewportHeight="108">',
     f'    <group android:translateX="{f(tx)}" android:translateY="{f(ty)}" android:scaleX="{f(sc*1000/1000)}" android:scaleY="{f(sc)}">']
x[-1] = f'    <group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" android:scaleX="{sc:.5f}" android:scaleY="{sc:.5f}">'
for i, (s, lx) in enumerate(L):
    x.append(f'        <group android:translateX="{lx:.3f}" android:scaleX="{s:.5f}" android:scaleY="{s:.5f}">')
    if i > 0:
        x.append(f'            <path android:pathData="{path}" android:strokeColor="{BG}" android:strokeWidth="{gap/s/sc:.3f}" android:strokeLineJoin="round" />')
    a, b = GRADS[i]
    x += [f'            <path android:pathData="{path}">',
          '                <aapt:attr name="android:fillColor">',
          f'                    <gradient android:type="linear" android:startX="0" android:startY="-{BY}" android:endX="{BX}" android:endY="{BY}"',
          f'                        android:startColor="{a}" android:endColor="{b}" />',
          '                </aapt:attr>', '            </path>']
    x.append('        </group>')
x += ['    </group>', '</vector>', '']
open(f'{REPO}/app/src/main/res/drawable-v26/ic_launcher_foreground.xml', 'w').write('\n'.join(x))
open(f'{REPO}/app/src/main/res/drawable-v26/ic_launcher_background.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<!-- tri.iptv launcher background: dark navy diagonal gradient. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:startColor="#FF141B28"
                android:endColor="#FF0B0F18" />
        </aapt:attr>
    </path>
</vector>
''')
# numbers for LogoMark
_, sc1, _, _, bb1, oxm = place(bb[3]-bb[1], 0, 0)
json.dump(dict(icon=icon('tri', R, d=D), banner=banner('tri', R, d=D), path=path, layers=L, bbox=bb, ox_mid=ox_mid,
               gap_units=4 * (bb[3] - bb[1]) / 92), open('export.json', 'w'))
print(json.dumps(dict(layers=L, bbox=bb, ox_mid=ox_mid, gap_units=4 * (bb[3] - bb[1]) / 92, path=path), indent=1))
