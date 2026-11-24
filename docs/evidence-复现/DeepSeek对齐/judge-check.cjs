// 用项目里真实的 renderMarkdown 跑一组「易混淆」片段，看它判成什么块
const { chromium } = require('playwright-core');
const cases = {
  '单行竖线（无分隔行）': '| a | b |',
  '两行表格': '| 参数 | 默认值 |\n| --- | --- |\n| limit | 60 |',
  '单独 ---': '---',
  '列表 vs 分割线': '- 第一项\n- 第二项\n\n- - -',
  '代码块内的表格语法': '```text\n| a | b |\n| --- | --- |\n```',
  '代码块内的井号标题': '```bash\n# 这是注释，不是标题\n```',
  '引用里的表格': '> | a | b |\n> | --- | --- |\n> | 1 | 2 |',
  '缩进四空格的代码（非围栏）': '    indented code?'
};
(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const p = await b.newPage();
  await p.goto('file:///F:/coding demo/src/main/resources/static/index.html', { waitUntil: 'domcontentloaded' });
  const out = await p.evaluate(cs => {
    const seen = o => [...o.children].map(c => c.tagName.toLowerCase()).join(' > ');
    return Object.entries(cs).map(([name, md]) => {
      const box = document.createElement('div');
      box.innerHTML = renderMarkdown(md);
      return { name, blocks: seen(box.firstElementChild), text: box.textContent.trim().slice(0, 30) };
    });
  }, cases);
  for (const r of out) console.log(`${r.name.padEnd(22, ' ')} -> ${r.blocks.padEnd(28, ' ')} | ${r.text}`);
  await b.close();
})();
