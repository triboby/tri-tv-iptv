const { chromium } = require('playwright');
const fs = require('fs');
const repo = process.argv[2];
const ex = JSON.parse(fs.readFileSync('export.json'));
const res = repo + '/app/src/main/res/';
(async () => {
  const b = await chromium.launch();
  const shot = async (svg, w, h, out, font) => {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    const head = font ? '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@600&text=%E7%9B%B4%E6%92%AD&display=block">' : '';
    await p.setContent(`<!doctype html><html><head>${head}<style>html,body{margin:0;background:transparent}svg{display:block;width:${w}px;height:${h}px}</style></head><body>${svg}</body></html>`);
    if (font) { await p.evaluate(() => document.fonts.load('600 84px "Noto Sans SC"', '直播')); await p.evaluate(() => document.fonts.ready);
      console.log('font ok:', await p.evaluate(() => document.fonts.check('600 84px "Noto Sans SC"', '直播'))); }
    await p.screenshot({ path: out, omitBackground: true });
    await p.close();
  };
  for (const [d, s] of [['mdpi', 48], ['hdpi', 72], ['xhdpi', 96], ['xxhdpi', 144], ['xxxhdpi', 192]])
    await shot(ex.icon, s, s, `${res}mipmap-${d}/ic_launcher.png`);
  await shot(ex.icon, 192, 192, repo + '/docs/icon.png');
  await shot(ex.banner, 640, 360, `${res}drawable-xhdpi/banner.png`, true);
  await b.close();
})();
