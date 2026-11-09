# 图片识别（Vision）

## 核心思路

多模态 = **文本 + 图片（作为 DataBlock）** 组装成 `Msg`，一起发给支持 vision 的模型。

```
用户上传图片 → MultipartFile
        │
        ▼
DataBlock.builder()
    .source(Base64Source.builder()
        .data(base64)
        .mediaType("image/png")
        .build())
    .build()
        │
        ▼
Msg.builder().role(MsgRole.USER).textContent("描述这张图是什么颜色").content(imageBlock).build()
        │
        ▼
ReActAgent.call(msg).block().getTextContent()
        │
        ▼
模型 /chat/completions 请求体:
{ content: [
    { type: "text", text: "..." },
    { type: "image_url", image_url: { url: "data:image/png;base64,..." } }
] }
```

## 后端接口

```java
@PostMapping(value = "/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Map<String, String> vision(
        @RequestParam("image") MultipartFile file,
        @RequestParam(value = "message", defaultValue = "描述这张图片") String message,
        @RequestParam(value = "model", defaultValue = "ollama-qwen3-vl") String modelId) throws IOException {

    // 1. AgentScope 的 Base64Source 直接接收 Base64 字符串, 先自己编码
    String base64 = Base64.getEncoder().encodeToString(file.getBytes());

    // 2. DataBlock 包装图片来源 + MIME 类型
    DataBlock imageBlock = DataBlock.builder()
            .source(Base64Source.builder()
                    .data(base64)
                    .mediaType(file.getContentType())   // 如 image/png
                    .build())
            .build();

    // 3. Msg 同时携带文本和图片 DataBlock
    Msg msg = Msg.builder()
            .role(MsgRole.USER)
            .textContent(message)
            .content(imageBlock)
            .build();

    // 4. 模型交给 ReActAgent, 同步取回复
    OpenAIChatModel model = factory.getOrCreate(modelId);
    try (ReActAgent agent = ReActAgent.builder().name("assistant").model(model).build()) {
        String reply = agent.call(msg).block().getTextContent();
        return Map.of("reply", reply == null ? "" : reply);
    }
}
```

**AgentScope 多模态关键 3 个类**：

| 类 | 作用 |
|---|---|
| `Msg.builder().role(MsgRole.USER).textContent()/content()` | 组装消息，文本与图片块一起提交 |
| `DataBlock.builder().source(...).build()` | 图片等非文本数据块 |
| `Base64Source.builder().data().mediaType().build()` | Base64 数据来源 + MIME 类型 |

**和迁移前的 Spring AI 写法对比**：
```java
// 迁移前 Spring AI: Resource 传原始字节, Media 内部自己编码
UserMessage.builder()
    .text(text)
    .media(new Media(MimeTypeUtils.parseMimeType(contentType), new ByteArrayResource(file.getBytes())))
    .build();

// 现在 AgentScope: 先 Base64 编码, 再包成 DataBlock
DataBlock.builder()
    .source(Base64Source.builder()
        .data(Base64.getEncoder().encodeToString(file.getBytes()))
        .mediaType(contentType)
        .build())
    .build();
```

AgentScope 不再替我们做 Base64 编码，需要显式交给 `Base64Source`。

## 前端：有图片自动分流

```javascript
async function sendMessage() {
    if (hasImage) {
        // 有图片 → FormData → /api/vision
        const fd = new FormData();
        fd.append('image', attachedImage.file);   // 原始文件对象
        fd.append('message', text);
        fd.append('model', currentModel);
        resp = await fetch('/api/vision', { method: 'POST', body: fd });
    } else {
        // 无图片 → JSON → /api/chat
        resp = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text, model: currentModel })
        });
    }
}
```

上传按钮和预览：
```javascript
// FileReader 读成 dataUrl 显示缩略图
imageInput.addEventListener('change', e => {
    const file = e.target.files[0];
    const reader = new FileReader();
    reader.onload = ev => {
        attachedImage = { file, dataUrl: ev.target.result };
        // dataUrl 直接塞 <img src="..."> 显示预览
    };
    reader.readAsDataURL(file);
});
```

## 哪些模型支持 Vision

| 模型 | 支持 | 备注 |
|---|---|---|
| `ollama-qwen3-vl` | ✅ | 本地，Ollama 原生 vision |
| `remote-deepseek` (deepseek-flash) | ✅ | DeepSeek 后端自动路由到 flash，支持图片 |
| `ollama-deepseek-r1` | ❌ | R1 是推理模型，无视觉 |

## 踩过的坑

**坑 1：图片要先转 Base64**

`Base64Source` 接收的是 Base64 字符串，不是原始字节。流程固定为：`file.getBytes()` → `Base64.getEncoder().encodeToString()` → `Base64Source.data(...)`。

**坑 2：mediaType 传 MIME 字符串**

`Base64Source.mediaType(...)` 接收 `"image/png"` 这类字符串，直接用 `file.getContentType()` 即可，不需要再套 Spring 的 `MimeType` / `MimeTypeUtils`。

**坑 3：文本和图片都放进同一个 Msg**

文本用 `textContent(...)`，图片用 `content(imageBlock)`。只传 DataBlock 而漏掉问题文本，模型不知道要回答什么。

**坑 4：base-url 写完整 OpenAI 兼容端点**

同 01 文档。Vision 走的也是同一个 `OpenAIChatModel`，`baseUrl` 要带 `/v1`（如 `http://localhost:11434/v1`），AgentScope 不会自动拼接。
