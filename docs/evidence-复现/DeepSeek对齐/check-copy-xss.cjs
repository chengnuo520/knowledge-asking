const { chromium } = require('playwright-core');
(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 1200 }, permissions: ['clipboard-read', 'clipboard-write'] });
  const p = await ctx.newPage();
  await p.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
  await p.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  await p.fill('#inputBox', 'x'); await p.click('.send-btn');
  await p.waitForFunction(() => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 && !document.querySelector('.message.typing'));

  // 复制按钮：点击后按钮文案变化 + 剪贴板内容与代码原文一致
  const before = await p.evaluate(() => document.querySelector('.message.bot:last-of-type pre code').textContent);
  await p.click('.message.bot:last-of-type .md-copy');
  await p.waitForTimeout(300);
  const btnText = await p.evaluate(() => document.querySelector('.message.bot:last-of-type .md-copy').textContent);
  const clip = await p.evaluate(() => navigator.clipboard.readText());
  const norm = t => t.replace(/\r\n/g, '\n').trim();   // Windows 剪贴板会把换行统一成 CRLF
  console.log('复制按钮文案 =', btnText, '| 剪贴板与原文一致（忽略 CRLF） =', norm(clip) === norm(before), '| 长度', clip.length);

  // XSS 回归：直接在页面上下文里跑渲染函数
  const xss = await p.evaluate(() => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>\n\n[click](javascript:alert(2))\n\n**bold** 与 `code` 与 ~~del~~\n\n| a | b |\n| --- | --- |\n| <script>alert(3)</script> | `x` |');
    const box = document.createElement('div');
    box.innerHTML = html;
    return {
      img: box.querySelectorAll('img').length,
      script: box.querySelectorAll('script').length,
      anchors: [...box.querySelectorAll('a')].map(a => a.getAttribute('href')),
      strong: box.querySelectorAll('strong').length,
      del: box.querySelectorAll('del').length,
      cells: box.querySelectorAll('td').length,
      textKeepsLiteral: box.textContent.includes('<img src=x')
    };
  });
  console.log('XSS 回归 =', JSON.stringify(xss));
  await b.close();
})();
