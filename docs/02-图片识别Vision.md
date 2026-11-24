# 图片识别（Vision）

> 一句话：多模态消息 = **文本 + 图片 DataBlock** 组装成一个 `Msg`，一起发给支持 vision 的模型。
>
> 文档结构：一、业务目标｜二、理论知识｜三、实践操作｜四、测试题（1~5 层追问）

---

## 一、业务目标

### 1.1 场景

用户真正会问的问题，往往带图：

| 场景 | 用户输入 |
|---|---|
| 看不懂报错 | 一张堆栈截图 + "这个错误什么意思" |
| 问界面元素 | 一张界面截图 + "导出按钮在哪" |
| 比对两版样式 | 截图 + "和设计稿差在哪" |
| 读文档里的表格 | 表格截图 + "帮我把这张表转成文字" |

纯文本模型面对这些只能回答"请描述你的问题"——体验断崖式下降。

### 1.2 目标（可验收）

| # | 目标 | 验收标准 |
|---|---|---|
| 1 | 一次请求完成图文问答 | 前端选图 + 输入文字 → 一个回复，不需要先 OCR 再问 |
| 2 | 与文本问答共用同一套模型配置 | `/api/vision` 也走 `ChatModelFactory`，换模型不改代码 |
| 3 | 不支持的模型给出可读错误 | 用 `ollama-deepseek-r1` 发图 → 明确提示，而不是空回复 |
| 4 | 图片不落盘 | 内存里转 Base64 后直接发给模型，服务端不留文件 |

### 1.3 范围边界

- ❌ 不做**多图**（一次一张）
- ❌ 不做**图片持久化**（不存数据库/不存磁盘）
- ❌ 不做**OCR 结构化抽取**（不返回坐标、不返回表格结构）
- ❌ 不做**图片压缩/裁剪**（原图直传，超过模型限制会由模型侧报错）

---

## 二、理论知识

### 2.1 多模态消息长什么样

文本模型的消息体是：

```json
{ "role": "user", "content": "你好" }
```

多模态把 `content` 从**字符串**升级成**数组**：

```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "这张图是什么颜色" },
    { "type": "image_url", "image_url": { "url": "data:image/png;base64,iVBORw0..." } }
  ]
}
```

理解这一点，就理解了全部——所谓"支持视觉"，就是模型能解析数组里的 `image_url` 项。

### 2.2 图片怎么传：URL 还是 Base64

| 方式 | 写法 | 优点 | 缺点 |
|---|---|---|---|
| **公网 URL** | `{"url": "https://x.com/a.png"}` | 请求体小、快 | 要求图片可公网访问；本地文件、内网图、隐私图都不行 |
| **Base64 data URL**（本项目） | `{"url": "data:image/png;base64,..."}` | 无需图床、隐私不出内网 | 请求体膨胀约 33%；大图会让 token 与延迟上升 |

Base64 的体积换算：原始 100KB 的图 → Base64 后约 133KB。本项目选它，原因是**用户上传的是本地文件**，没有图床，也不应该为了问一张截图就去传第三方存储。

### 2.3 AgentScope 的三个类

| 类 | 作用 | 关键方法 |
|---|---|---|
| `Msg` | 一条消息，能同时装文本和内容块 | `.textContent("...")` + `.content(block)` |
| `DataBlock` | 非文本数据块（图片、文件） | `.source(...)` |
| `Base64Source` | Base64 数据来源 + MIME | `.data(base64)` `.mediaType("image/png")` |

组装顺序是固定的：

```
byte[] → Base64 字符串 → Base64Source → DataBlock → Msg → Agent
```

**和 Spring AI 的关键差异**：Spring AI 的 `Media` 接受 `Resource`（原始字节），内部自己编码；AgentScope 的 `Base64Source` **要求你传已经编码好的字符串**。少做一步就得到 null 或异常。

### 2.4 为什么 `mediaType` 必须传对

MIME 决定了模型怎么解析这串二进制。传错（比如 PNG 传成 `image/jpeg`）的后果：

- 轻则模型"看到"一张糊图，回答质量下降
- 重则直接 400（部分服务端会校验图片头与 MIME 是否一致）

`MultipartFile.getContentType()` 由浏览器给出，对正常图片是可信的；但要彻底稳妥，应该像文档解析那样用魔数校验（本项目暂未做，属于已知边界）。

### 2.5 哪些模型能看图

| 模型 | 支持 | 说明 |
|---|---|---|
| `ollama-qwen3-vl` | ✅ | 本地，Ollama 原生 vision，离线可用 |
| `remote-deepseek`（flash） | ✅ | 云端自动路由到支持图片的版本 |
| `ollama-deepseek-r1` | ❌ | 推理模型，无视觉通道 |

**这是业务上的真实约束**：模型下拉框里同时存在支持与不支持视觉的模型，所以前端必须在"带图发送"时**切换到支持 vision 的模型**，或者提示用户手动切换。本项目当前是"用户自己保证选对模型"——这是一个可改进点。

### 2.6 成本与限制

| 项 | 影响 |
|---|---|
| Token 消耗 | 图片按分辨率折算成 token（通常几百到上千），比纯文本贵一个量级 |
| 请求体大小 | Base64 膨胀 33%，Spring 默认 multipart 上限 1MB，需确认是否够用 |
| 延迟 | 视觉推理比纯文本慢，通常 2~5 秒 |
| 分辨率 | 多数模型会把超大图缩放到固定边长，再高的分辨率没有收益 |

---

## 三、实践操作

### 3.0 改动清单

| 位置 | 改动 |
|---|---|
| 后端 | 新增 `POST /api/vision`（multipart） |
| 前端 | 有图走 `/api/vision`，无图走 `/api/chat`；加缩略图预览 |
| 配置 | 无需新增（复用 `chat.models`，只需选一个支持视觉的模型） |

### 3.1 Step 1：后端接口

```java
@PostMapping(value = "/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Map<String, String> vision(
        @RequestParam("image") MultipartFile file,
        @RequestParam(value = "message", defaultValue = "描述这张图片") String message,
        @RequestParam(value = "model", defaultValue = "ollama-qwen3-vl") String modelId)
        throws IOException {

    // ① 自己编码：Base64Source 只收字符串，不做编码
    String base64 = Base64.getEncoder().encodeToString(file.getBytes());

    // ② 图片来源 + MIME
    DataBlock imageBlock = DataBlock.builder()
            .source(Base64Source.builder()
                    .data(base64)
                    .mediaType(file.getContentType())
                    .build())
            .build();

    // ③ 文本与图片装进同一条消息
    Msg msg = Msg.builder()
            .role(MsgRole.USER)
            .textContent(message)
            .content(imageBlock)
            .build();

    // ④ 交给 Agent 执行
    OpenAIChatModel model = factory.getOrCreate(modelId);
    try (ReActAgent agent = ReActAgent.builder().name("assistant").model(model).build()) {
        String reply = agent.call(msg).block().getTextContent();
        return Map.of("reply", reply == null ? "" : reply);
    }
}
```

### 3.2 Step 2：前端分流

```javascript
async function sendMessage() {
    const hasImage = !!attachedImage;

    if (hasImage) {
        const fd = new FormData();
        fd.append('image', attachedImage.file);     // 原始 File 对象
        fd.append('message', text || '描述这张图片');
        fd.append('model', currentModel);
        resp = await fetch('/api/vision', { method: 'POST', body: fd });
    } else {
        resp = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text, model: currentModel })
        });
    }
}
```

### 3.3 Step 3：缩略图预览

```javascript
imageInput.addEventListener('change', e => {
    const file = e.target.files[0];
    const reader = new FileReader();
    reader.onload = ev => {
        attachedImage = { file, dataUrl: ev.target.result };  // dataUrl 可直接塞进 <img src>
        renderPreview();
    };
    reader.readAsDataURL(file);
});
```

这里读出的 `dataUrl` 和发给后端的 Base64 **没有关系**——前端这个只用于显示缩略图，真正的编码在后端做。

> **📷 待补截图 ①**　点击 📎 后选择图片，输入框上方出现缩略图与删除按钮
> 存放位置：`evidence-基础功能/02-01-图片预览.png`

![图片预览](evidence-基础功能/02-01-图片预览.png)

### 3.4 Step 4：验证

```bash
# 用库里任意一张 png 验证（本项目有 docs/evidence-kb/01-上传弹窗.png）
curl -X POST http://localhost:8080/api/vision \
  -F "image=@docs/evidence-kb/01-上传弹窗.png" \
  -F "message=这张图里有哪些主要内容？" \
  -F "model=ollama-qwen3-vl"
# 期望：{"reply":"..."} 内容确实在描述这张图

# 用不支持视觉的模型，验证错误可读
curl -X POST http://localhost:8080/api/vision \
  -F "image=@docs/evidence-kb/01-上传弹窗.png" \
  -F "message=描述一下" \
  -F "model=ollama-deepseek-r1"
```

> **📷 待补截图 ②**　终端执行上面第一条 curl 的输出（能看到 reply 内容）
> 存放位置：`evidence-基础功能/02-02-curl验证.png`

![curl 验证](evidence-基础功能/02-02-curl验证.png)

> **📷 待补截图 ③**　前端上传截图后的完整问答气泡（用户侧有缩略图，机器人侧有回答）
> 存放位置：`evidence-基础功能/02-03-图文问答.png`

![图文问答](evidence-基础功能/02-03-图文问答.png)

> **📷 待补截图 ④**　用 `ollama-deepseek-r1` 发图时的错误提示（证明错误可读）
> 存放位置：`evidence-基础功能/02-04-不支持视觉的模型.png`

![不支持视觉的模型](evidence-基础功能/02-04-不支持视觉的模型.png)

### 3.5 踩坑记录

| 坑 | 现象 | 解法 |
|---|---|---|
| 忘了 Base64 编码 | 报错或空回复 | `Base64Source.data()` 收的是字符串，必须自己 `encodeToString` |
| 只传了图片没传文字 | 模型答非所问 | 文本用 `textContent()`，图片用 `content()`，两个都要 |
| mediaType 传成 null | 400 | `file.getContentType()` 对部分上传可能为 null，兜底 `image/png` |
| 选错模型 | 空回复或报不支持 | 前端应在有图时把模型切到 vision 模型（当前靠用户自觉） |
| 大图超 multipart 上限 | 413 / 上传失败 | 确认 `spring.servlet.multipart.max-file-size` |

---

## 四、测试题（面试向，1~5 层追问）

> 建议先自己想一遍再展开对照；每道题下面还有 5 层追问，逐层点开。

### Q1｜多模态消息在协议层是怎么表达的？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：<code>content</code> 从字符串升级为数组，元素是 <code>{type:&quot;text&quot;}</code> 与 <code>{type:&quot;image_url&quot;}</code>。</p><blockquote><p><strong>L1 追问</strong>：<code>image_url</code> 里的 url 可以是哪两种形式？<br>要点：公网可访问的 http(s) 地址，或 <code>data:</code> 开头的 Base64 data URL。</p></blockquote><blockquote><p><strong>L2 追问</strong>：为什么不直接把图片二进制塞进 JSON？<br>要点：JSON 是文本格式，二进制必须先编码（Base64）。直接塞会破坏编码，且多数网关会拒绝。</p></blockquote><blockquote><p><strong>L3 追问</strong>：Base64 会带来什么代价？怎么缓解？<br>要点：体积 +33%、token 与延迟上升。缓解：前端压缩（限制长边）、按需转码、或改用对象存储 + 签名 URL。</p></blockquote><blockquote><p><strong>L4 追问</strong>：如果图片必须保密，只能用 Base64 吗？<br>要点：不一定。可以用<strong>私有对象存储 + 短期签名 URL</strong>，既不让图片公开，也避免 Base64 膨胀；代价是要有存储与鉴权链路。</p></blockquote><blockquote><p><strong>L5 追问</strong>：Base64 图片会不会被 CDN/网关缓存，带来隐私风险？<br>要点：Base64 在请求体里，通常不进 CDN 缓存；但会进<strong>访问日志与 APM 采样</strong>，需要确保日志脱敏，否则截图内容可能落到日志系统里。这是最容易被忽略的隐私点。</p></blockquote>
</details>

### Q2｜AgentScope 的 `Msg / DataBlock / Base64Source` 各管什么？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：Msg 是消息容器，DataBlock 是非文本内容块，Base64Source 是数据来源与 MIME。</p><blockquote><p><strong>L1 追问</strong>：为什么要把&quot;内容&quot;和&quot;来源&quot;分成两层？<br>要点：解耦。同一张图可以来自 Base64、URL、文件 ID；<code>DataBlock</code> 只描述&quot;这是个什么数据&quot;，<code>Source</code> 描述&quot;从哪来&quot;。</p></blockquote><blockquote><p><strong>L2 追问</strong>：如果以后要支持&quot;文件（PDF）作为输入&quot;，这套结构能复用吗？<br>要点：能。<code>DataBlock</code> + 对应 Source 即可，模型侧按 MIME 分派；这也是这套抽象的价值。</p></blockquote><blockquote><p><strong>L3 追问</strong>：<code>mediaType</code> 传错会怎样？怎么保证正确？<br>要点：轻则质量下降，重则 400。稳妥做法是用魔数校验（Tika 的 detector 就能做），而不是只信 <code>Content-Type</code>。</p></blockquote><blockquote><p><strong>L4 追问</strong>：为什么文本和图片要放在<strong>同一个</strong> <code>Msg</code> 里？<br>要点：模型看到的是&quot;一条消息里的多个部分&quot;，语义上它们是一次输入；分成两条消息会变成两轮对话，模型可能把图片当成&quot;上一轮的上下文&quot;。</p></blockquote><blockquote><p><strong>L5 追问</strong>：如果要一次问两张图做对比，怎么做？有什么限制？<br>要点：<code>content</code> 里放两个 DataBlock。限制是 token 翻倍、部分模型只支持单图、以及&quot;哪张是哪张&quot;要靠文字明确指代（图1/图2），否则模型会混淆。</p></blockquote>
</details>

### Q3｜为什么图片不落盘？如果要求留存呢？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：当前是一次性问答，落盘没有业务价值，还会带来隐私与清理成本。</p><blockquote><p><strong>L1 追问</strong>：不落盘的话，用户刷新页面还能看到自己发的图吗？<br>要点：不能。前端只有本地 <code>dataUrl</code>，刷新即丢。这是当前的设计取舍（无会话持久化）。</p></blockquote><blockquote><p><strong>L2 追问</strong>：如果要留存，存哪？数据库还是对象存储？<br>要点：<strong>对象存储 + 数据库存引用</strong>。图片二进制放 DB 会让备份膨胀、查询变慢；DB 只存 key/URL。</p></blockquote><blockquote><p><strong>L3 追问</strong>：留存会引入哪些新问题？<br>要点：隐私合规（谁有权看）、生命周期（保留多久）、去重（同一张图重复上传）、访问控制（URL 不能裸奔）。</p></blockquote><blockquote><p><strong>L4 追问</strong>：怎么做去重？<br>要点：对图片字节做哈希（SHA-256），同哈希复用已有对象；和文档内容哈希是同一套思路。</p></blockquote><blockquote><p><strong>L5 追问</strong>：如果图片里含身份证、银行卡，怎么处理？<br>要点：三件事——上传即脱敏/打码（需要检测模型）、存储加密与最小权限、以及<strong>保留期到点自动删除</strong>。技术上还要确保缩略图、日志、备份里都没有原图残留。</p></blockquote>
</details>

### Q4｜为什么前端在"有图"时要切到 vision 模型？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：因为模型能力不同，不支持的模型会报错或返回空。</p><blockquote><p><strong>L1 追问</strong>：不切会怎样？用户看到什么？<br>要点：<code>ollama-deepseek-r1</code> 会返回报错或空回复，用户以为&quot;图片没发出去&quot;。</p></blockquote><blockquote><p><strong>L2 追问</strong>：更好的做法是什么？<br>要点：在模型列表里加 <code>capabilities: [&quot;vision&quot;]</code> 标记，前端选图时自动过滤/切换并在 UI 上说明&quot;已为你切到支持看图的模型&quot;。</p></blockquote><blockquote><p><strong>L3 追问</strong>：自动切换会不会让用户困惑（模型被偷偷换了）？<br>要点：会。所以要<strong>显式提示</strong>，最好在气泡上标注实际使用的模型，而不是静默替换。</p></blockquote><blockquote><p><strong>L4 追问</strong>：后端要不要也校验？<br>要点：要。前端的约束是&quot;体验&quot;，后端才是&quot;边界&quot;——后端应根据模型能力表拒绝不支持视觉的模型 + 图片组合，返回明确错误。</p></blockquote><blockquote><p><strong>L5 追问</strong>：能力表放配置还是数据库？考虑什么？<br>要点：放配置（随代码走）够用；如果要运营动态调整模型池，就得放数据库并缓存。关键是一处定义、前后端共用同一份语义（可以通过 <code>/api/models</code> 下发 capabilities 实现）。</p></blockquote>
</details>

### Q5｜图片识别的成本怎么估？怎么控？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：按图片折算的 token + 输出 token 计价，比纯文本贵一个量级。</p><blockquote><p><strong>L1 追问</strong>：一张 1920×1080 的截图大概折算多少 token？<br>要点：取决于模型的分块规则，常见在几百到一千多 token。精确值要看厂商文档，不能凭感觉估。</p></blockquote><blockquote><p><strong>L2 追问</strong>：怎么降低单次成本？<br>要点：前端压缩长边（如 1024）、只截需要问的区域、避免整屏截图。</p></blockquote><blockquote><p><strong>L3 追问</strong>：成本会不会因为&quot;缓存命中&quot;降低？<br>要点：部分厂商对相同前缀有缓存优惠，但图片内容变化就意味着不命中；同一张图重复问才可能受益。</p></blockquote><blockquote><p><strong>L4 追问</strong>：怎么做成本监控？<br>要点：从响应 usage 里取 token 数，按模型单价累计，并按用户/接口维度打点。没有 usage 透出就只能估算。</p></blockquote><blockquote><p><strong>L5 追问</strong>：如果发现某用户一天消耗了 100 倍的正常量，怎么办？<br>要点：先有配额与限流（本项目是 60 次/分），再有分级告警与熔断；根因上要区分&quot;滥用&quot;与&quot;业务真实增长&quot;，不要一刀切。</p></blockquote>
</details>

### Q6｜这套实现有什么安全隐患？

<details>
<summary>参考答案 + 5 层追问</summary>
<p><strong>参考答</strong>：图片内容不可信、MIME 可伪造、大图可打满内存。</p><blockquote><p><strong>L1 追问</strong>：图片里能不能藏提示词注入？<br>要点：能（图片里写&quot;忽略之前的指令&quot;）。模型可能照做，因此不能把图片内容当作可信指令源，敏感操作必须二次确认。</p></blockquote><blockquote><p><strong>L2 追问</strong>：<code>getContentType()</code> 可伪造吗？<br>要点：可伪造（浏览器/客户端可自定义 multipart 头）。应该用魔数校验真实类型，并只放行白名单。</p></blockquote><blockquote><p><strong>L3 追问</strong>：超大图片或畸形图片会怎样？<br>要点：<code>file.getBytes()</code> 直接进内存，可能 OOM；畸形图片可能触发解码器漏洞。需要限制大小 + 超时 + 及时升级依赖。</p></blockquote><blockquote><p><strong>L4 追问</strong>：如果图片里是恶意二维码/链接，模型读出链接，前端会不会自动渲染？<br>要点：会，如果前端把模型输出直接 <code>innerHTML</code>。所以渲染必须消毒（本项目用 DOMPurify），链接只放行 http(s)。</p></blockquote><blockquote><p><strong>L5 追问</strong>：怎么系统性地回归这些安全问题？<br>要点：建一组&quot;恶意样本&quot;（含注入文字、伪造 MIME、超大图、畸形图），做成自动化用例，每次改动跑一遍；这比事后排查便宜得多。</p></blockquote>
</details>
