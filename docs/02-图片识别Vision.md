# 图片识别（Vision）

## 核心思路

多模态 = **文本 + 图片（作为 Media）** 组装成 `UserMessage`，一起发给支持 vision 的模型。

```
用户上传图片 → MultipartFile
        │
        ▼
UserMessage.builder()
    .text("描述这张图是什么颜色")
    .media(new Media(MimeType, ByteArrayResource))
    .build()
        │
        ▼
模型 /v1/chat/completions 请求体:
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
        @RequestParam(value = "model", defaultValue = "ollama-qwen3-vl") String modelId) {

    // 1. MimeType —— 用 Spring 的 MimeTypeUtils.parseMimeType, 别直接 new MimeType("image/png")
    //    Spring 的 MimeType 构造函数不接受 "/" 分隔的完整字符串!
    MimeType mimeType = MimeTypeUtils.parseMimeType(file.getContentType());

    // 2. Media —— Spring AI 用 Resource 接口包装字节
    Media media = new Media(mimeType, new ByteArrayResource(file.getBytes()));

    // 3. UserMessage.builder() 链式组装
    UserMessage userMessage = UserMessage.builder()
            .text(message)
            .media(media)
            .build();

    // 4. ChatModel.call(message) —— 一行搞定
    String reply = factory.getOrCreate(modelId).call(userMessage);
    return Map.of("reply", reply == null ? "" : reply);
}
```

**Spring AI 多模态关键 3 个类**：

| 类 | 包 | 作用 |
|---|---|---|
| `UserMessage.builder().text().media().build()` | `org.springframework.ai.chat.messages` | 组装消息 |
| `Media(MimeType, Resource)` | `org.springframework.ai.content` | 图片（或其他媒体） |
| `MimeTypeUtils.parseMimeType(String)` | `org.springframework.util` | 安全解析 `"image/png"` |

**和 LangChain4j 对比**：
```java
// LangChain4j: 手动 Base64 + 两个 Content 对象
String b64 = Base64.getEncoder().encodeToString(file.getBytes());
UserMessage.from(TextContent.from(text), ImageContent.from(b64, mime));

// Spring AI: 不用自己转 Base64, Resource 传原始字节就行
UserMessage.builder().text(text).media(new Media(mime, new ByteArrayResource(file.getBytes()))).build();
```

Spring AI 的 `Media` 内部自己处理 Base64 编码，我们省了一步。

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

**坑 1：MimeType 构造函数报 `Invalid token character '/'`**

```
// ❌ 不行 —— Spring 的 MimeType 构造函数不接受 "/"
new MimeType("image/png")

// ✅ 可以 —— parseMimeType 能正确处理
MimeTypeUtils.parseMimeType("image/png")

// ✅ 也可以 —— 分开传 type 和 subtype
new MimeType("image", "png")
```

**坑 2：MediaType vs MimeType**

注意这俩不是一个东西：
- `org.springframework.http.MediaType` —— HTTP Content-Type 用的，Spring Boot 自动导入
- `org.springframework.util.MimeType` —— 底层类型，Spring AI 的 Media 构造函数要这个

Spring AI 很多 API 用的是 `MimeType`，别搞混了 import。

**坑 3：Resource 接口**

Spring AI 的 `Media` 需要一个 `Resource` 实现。常用的几个：
- `new ByteArrayResource(bytes)` —— 内存中的字节
- `new FileSystemResource(new File("a.jpg"))` —— 本地文件
- `new UrlResource("https://...")` —— 网络图片

我们用 `ByteArrayResource(file.getBytes())` 最简单。

**坑 4：base-url 不带 `/v1`**

同 01 文档。Vision 走的也是同一个 `OpenAiApi`，base-url 规则一样。
