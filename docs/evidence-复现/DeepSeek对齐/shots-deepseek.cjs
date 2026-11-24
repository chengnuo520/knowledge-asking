// 对比截图脚本：向桩模型发一条指令，把富文本回复渲染出来并分段截图
// 用法：NODE_PATH=<repo>/.workbuddy/tools/node_modules node shots-deepseek.cjs --out=before
// 产物：<out>-01-整体.png / <out>-02-代码块.png / <out>-03-表格.png + 控制台渲染统计
const path = require('path');
const { chromium } = require('playwright-core');

const outArg = process.argv.find(a => a.startsWith('--out='));
const OUT = outArg ? outArg.slice('--out='.length) : 'shot';
const DIR = __dirname;
const BASE = process.env.BASE_URL || 'http://localhost:8080';
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const VIEWPORT = { width: 1440, height: 2400 };

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: VIEWPORT, locale: 'zh-CN' });
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  await page.selectOption('#modelSelect', 'remote-deepseek');

  await page.fill('#inputBox', '把接口限流的方案完整讲一遍');
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'),
    null, { timeout: 60000 });
  await page.waitForTimeout(600);

  const stats = await page.evaluate(() => {
    const bubble = [...document.querySelectorAll('.message.bot:not(.typing)')].pop();
    const pick = sel => bubble.querySelectorAll(sel).length;
    return {
      headings: ['h1', 'h2', 'h3', 'h4'].map(t => t + '=' + pick(t)).join(' '),
      tables: pick('table'), tableRows: pick('table tr'),
      codeBlocks: pick('pre'), highlightedSpans: pick('pre span[class*="tok"]'),
      inlineCode: pick('code:not(pre code)'),
      blockquote: pick('blockquote'),
      lists: pick('ul, ol'), listItems: pick('li'),
      links: pick('a[href]'),
      strong: pick('strong'), em: pick('em'), del: pick('del'),
      hr: pick('hr'),
      rawMarkdownLeft: /\*\*|```|^\|/m.test(bubble.textContent),
      height: Math.round(bubble.getBoundingClientRect().height)
    };
  });
  console.log('渲染统计 =', JSON.stringify(stats, null, 2));

  // 整体：气泡顶部对齐视口
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('.message.bot:not(.typing)')].pop();
    b.scrollIntoView({ block: 'start' });
  });
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(DIR, `${OUT}-01-整体.png`) });

  async function shotPart(sel, name) {
    const box = await page.evaluate(s => {
      const el = document.querySelector(s);
      if (!el) return null;
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      return { x: Math.max(0, r.x - 24), y: Math.max(0, r.y - 24), width: r.width + 48, height: r.height + 48 };
    }, sel);
    if (!box) { console.warn('未找到元素:', sel); return; }
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(DIR, `${OUT}-${name}.png`), clip: box });
    console.log('已截图', name, JSON.stringify(box));
  }

  await shotPart('.message.bot:last-of-type pre', '02-代码块');
  await shotPart('.message.bot:last-of-type table', '03-表格');

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
