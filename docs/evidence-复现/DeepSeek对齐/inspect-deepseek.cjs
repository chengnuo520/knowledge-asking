// 取证脚本：抓取 DeepSeek 网页端（chat.deepseek.com）Markdown 渲染相关的真实 CSS 规则
// 用途：给「对齐 DeepSeek 展示规范」提供客观依据，而不是凭印象写样式
// 运行：NODE_PATH=<repo>/.workbuddy/tools/node_modules node inspect-deepseek.cjs
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');

const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const OUT = path.join(__dirname, 'deepseek-css-dump.txt');

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 }, locale: 'zh-CN' });
  const cssUrls = new Set();
  page.on('response', r => {
    const u = r.url();
    if (/\.css(\?|$)/.test(u)) cssUrls.add(u);
  });

  await page.goto('https://chat.deepseek.com/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(8000);

  const inlined = await page.evaluate(() => {
    const parts = [];
    document.querySelectorAll('style').forEach(s => parts.push(s.textContent || ''));
    document.querySelectorAll('link[rel="stylesheet"]').forEach(l => { if (l.href) parts.push('@import-url:' + l.href); });
    return parts.join('\n/* ---- */\n');
  });

  const urlList = await page.evaluate(() =>
    [...document.querySelectorAll('link[rel="stylesheet"]')].map(l => l.href).filter(Boolean));
  urlList.forEach(u => cssUrls.add(u));

  let remote = '';
  for (const u of cssUrls) {
    try {
      const resp = await page.request.get(u, { timeout: 30000 });
      remote += `\n\n/* ==================== ${u} ==================== */\n` + (await resp.text());
    } catch (e) {
      remote += `\n\n/* FAILED ${u}: ${e.message} */`;
    }
  }

  const all = inlined + remote;
  fs.writeFileSync(OUT, all, 'utf8');

  // 只挑 markdown / 代码块相关的规则，便于人读
  const wanted = [];
  const blocks = all.split('}');
  for (const b of blocks) {
    if (/ds-markdown|code-block|md-code|markdown/i.test(b)) wanted.push(b.trim() + '}');
  }
  const picked = path.join(__dirname, 'deepseek-css-markdown.txt');
  fs.writeFileSync(picked, wanted.join('\n\n'), 'utf8');
  console.log('CSS 总长度 =', all.length, ' 命中规则数 =', wanted.length);
  console.log('样式表 URL 数 =', cssUrls.size);
  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
