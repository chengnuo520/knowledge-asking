// 最小的 OpenAI 兼容桩服务（无密钥也能验证整条调用链）
// 用法: node stub-openai.cjs [port]   —— 默认 9099
// 作用: 接收 POST .../chat/completions，把收到的 messages 结构打印出来，
//       并回一条固定格式的 assistant 消息，用于证明「请求确实到达了这个 base-url」。
const http = require('http');
const fs = require('fs');

const PORT = Number(process.argv[2] || 9099);

// 可选：node stub-openai.cjs 9099 --reply-file=xxx.md
// 指定后，固定用该文件内容作为 assistant 回复（用于验证前端渲染/XSS 防护）
const replyArg = process.argv.slice(3).find(a => a.startsWith('--reply-file='));
const replyFile = replyArg ? replyArg.slice('--reply-file='.length) : null;

function summarizePart(p) {
  if (p.type === 'text') return `text[${p.text.length}字] "${p.text}"`;
  if (p.type === 'image_url') {
    const url = (p.image_url && p.image_url.url) || '';
    const m = /^data:([^;]+);base64,(.*)$/s.exec(url);
    return m
      ? `image_url[mediaType=${m[1]} base64长度=${m[2].length}]`
      : `image_url[非data-url 长度=${url.length}]`;
  }
  return `other[type=${p.type}]`;
}

const server = http.createServer((req, res) => {
  let raw = '';
  req.on('data', c => (raw += c));
  req.on('end', () => {
    let body = {};
    try { body = JSON.parse(raw || '{}'); } catch (_) { /* 忽略 */ }
    const msgs = body.messages || [];
    const last = msgs[msgs.length - 1] || {};
    const parts = Array.isArray(last.content)
      ? last.content.map(summarizePart).join(' + ')
      : String(last.content || '');

    const line = `[stub] ${req.method} ${req.url} model=${body.model} role=${last.role} content=${parts}`;
    console.log(line);

    const reply = replyFile
      ? fs.readFileSync(replyFile, 'utf8')
      : `【桩回复】已收到请求：path=${req.url} model=${body.model}；内容=${parts}`;
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      id: 'stub-' + Date.now(),
      object: 'chat.completion',
      created: Math.floor(Date.now() / 1000),
      model: body.model || 'stub-model',
      choices: [{ index: 0, message: { role: 'assistant', content: reply }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
    }));
  });
});

server.listen(PORT, () => console.log(`[stub] listening on http://localhost:${PORT}`));
