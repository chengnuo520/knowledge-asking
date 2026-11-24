// 用 Chrome 把整图裁成局部（避免安装图像库）：node crop.cjs <in.png> <out.png> x y w h
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');
const [inp, out, x, y, w, h] = process.argv.slice(2);
(async () => {
  const b64 = fs.readFileSync(path.resolve(inp)).toString('base64');
  const b = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const p = await b.newPage({ viewport: { width: 1600, height: 2600 } });
  await p.setContent(`<body style="margin:0"><img src="data:image/png;base64,${b64}" style="display:block"></body>`);
  await p.waitForTimeout(400);
  await p.screenshot({ path: out, clip: { x: +x, y: +y, width: +w, height: +h } });
  console.log('cropped ->', out);
  await b.close();
})();
