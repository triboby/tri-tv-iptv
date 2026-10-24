# Logo source

The launcher icon, adaptive icon vectors and TV banner are generated from these scripts.

Construction (H = 110, the front triangle's left edge):

- Apex at H·φ/2, so each half of the triangle is a golden rectangle.
- Corner radius H/φ⁴ ≈ 16.
- Two smaller layers behind, each shrunk by φ^-1/5 ≈ 0.908 and stepped 16 to the left.
- Icon mark height = tile / φ²; banner type 84 at weight 600 in white, mark 136 (84·φ), gap 52 (84/φ).

Regenerate (needs Python 3 and Node with Playwright; the banner font loads from Google Fonts):

```
cd design/logo
python3 export.py ../..     # writes the adaptive icon vectors and export.json
node render.js ../..        # writes the mipmap PNGs, docs/icon.png and the banner
```
