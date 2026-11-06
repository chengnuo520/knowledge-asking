# LangChain4j vs Spring AI — 选型对比

## 设计哲学的根本分歧

```
LangChain4j:  "你想怎么用都行, 我不拦着"    ← 纯 Java 工具库, 提供 API
Spring AI:    "按我的规矩来, 我帮你全自动"  ← Spring Boot 生态扩展, 接管配置
```

这条决定了后面所有差异。

## 依赖管理

**LangChain4j** — 一个 OpenAI 兼容 = 一个依赖：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.34.0</version>
</dependency>
<!-- 搞定 Ollama + DeepSeek + DashScope + 任何 OpenAI 兼容端点 -->
```

**Spring AI** — 每个模型供应商 = 一个 starter：

```xml
<dependency>spring-ai-starter-model-ollama</dependency>     <!-- Ollama -->
<dependency>spring-ai-starter-model-openai</dependency>    <!-- OpenAI/DeepSeek -->
<!-- DashScope 要找阿里第三方 starter -->
```

差距：**3 个模型 → LangChain4j 1 个依赖，Spring AI 2+ 个 starter**。

## 创建模型实例

**LangChain4j** — 单层 builder，直接 new：

```java
OpenAiChatModel.builder()
    .baseUrl("https://api.deepseek.com/v1")
    .apiKey("sk-xxx")
    .modelName("deepseek-chat")
    .temperature(0.7)
    .build();
```

**Spring AI** — 三层结构，先 API 再 Options 再 Model：

```java
OpenAiApi api = OpenAiApi.builder()
    .baseUrl("https://api.deepseek.com")   // 注意: 不带 /v1!
    .apiKey("sk-xxx")
    .build();

OpenAiChatOptions options = OpenAiChatOptions.builder()
    .model("deepseek-flash")
    .temperature(0.7)
    .build();

OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
```

Spring AI 多一层嵌套，但把**连接层（OpenAiApi）和参数层（Options）分开了**，结构更清晰。

## 调用 API

**LangChain4j**：

```java
// 简单聊天
String reply = model.chat("你好");

// 多消息
String reply = model.chat(
    SystemMessage.from("你是翻译"),
    UserMessage.from(text)
);

// Vision
UserMessage.from(
    TextContent.from("描述这张图"),
    ImageContent.from(base64, "image/png")
);
```

**Spring AI**：

```java
// 简单聊天
String reply = model.call("你好");    // ← 一行!

// 多消息
String reply = model.call("你是翻译", text);

// Vision — 不用手动转 Base64, Resource 传原始字节
UserMessage.builder()
    .text("描述这张图")
    .media(new Media(MimeTypeUtils.parseMimeType(contentType), new ByteArrayResource(file.getBytes())))
    .build();
```

**Spring AI `model.call(String)` 一行搞定简单聊天**，LangChain4j 至少两行（`ChatRequest.builder().messages(UserMessage.from(text)).build(); model.chat(req)`）。

Vision 方面：LangChain4j 要手动 `Base64.getEncoder().encodeToString()`，Spring AI 的 `Media` 内部自己处理。

## 自动配置 vs 手动装配

这是**迁移中踩到最大的坑**，也是两个框架最核心的冲突点。

**Spring AI 的设计：自动配置**

引了 `spring-ai-starter-model-openai` 后，Spring Boot 启动时会自动执行：

```
OpenAiChatAutoConfiguration
OpenAiAudioSpeechAutoConfiguration
OpenAiAudioTranscriptionAutoConfiguration
OpenAiEmbeddingAutoConfiguration
OpenAiImageAutoConfiguration
OpenAiModerationAutoConfiguration
OllamaChatAutoConfiguration
OllamaEmbeddingAutoConfiguration
```

它会**强制**你在 yml 里写 `spring.ai.openai.api-key`，然后自动创建 `OpenAiChatModel` Bean。**你不需要写 @Configuration，但也不能绕过它**。

**LangChain4j 的设计：零自动配置**

引了 langchain4j-open-ai 后，什么都不会自动发生。你想 `new OpenAiChatModel.builder()` 就 new，想工厂模式就工厂，完全自由。

**我们的解决方案：exclude 掉 Spring AI 的自动配置**

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration
      # ... 其他不需要的 AI 自动配置
```

然后自己在 ChatModelFactory 里手动创建实例。**绕了一圈，Spring AI 又变回了 LangChain4j 的使用方式**——API 风格更现代，但自由度靠自己 exclude 出来。

## base-url 规则不同

| | LangChain4j | Spring AI |
|---|---|---|
| 期望格式 | `https://api.deepseek.com/v1` | `https://api.deepseek.com` |
| 拼 URL | `baseUrl + "/chat/completions"` | `baseUrl + "/v1/chat/completions"` |
| 写 yml 时 | 完整 URL | 根地址，不带 `/v1` |

**迁移时最容易忘这一条**。LangChain4j 时代你写的 `/v1` 到 Spring AI 会变成 `/v1/v1/chat/completions` → 404。

## 完整对比表

| 维度 | LangChain4j | Spring AI | 说明 |
|---|---|---|---|
| 设计哲学 | 工具库，不管你 | Spring 生态扩展，帮你全自动 | 核心分歧 |
| 多模型依赖 | 1 个 open-ai 覆盖 N 模型 | N 个 starter | LangChain4j 赢 |
| 配置灵活度 | yml 结构自定义 | yml key 由框架规定 | LangChain4j 赢 |
| 动态切换模型 | 工厂 + cache 天经地义 | 要 exclude 自动配置才能写 | LangChain4j 赢 |
| 代码侵入性 | 纯 Java 库 | 自动装配强绑定 | LangChain4j 赢 |
| 调用简洁度 | `model.chat("...")` | `model.call("...")` 更短 | Spring AI 赢 |
| 流式返回 | 回调风格 `onNext().onComplete()` | Reactor Flux | Spring 开发者偏好 |
| Vision API | 手动 Base64 + Content 组合 | Resource 传字节 + Builder 链式 | Spring AI 更简洁 |
| 自动配置 | 无（优点） | 有（默认抢跑） | LangChain4j 赢 |
| Vector Store | 各库独立依赖 | VectorStore starter 统一 | Spring AI 赢 |
| Observability | 自己打日志 | Micrometer + OTel 开箱即用 | Spring AI 碾压 |
| API 风格 | 方法重载，参数驱动 | Builder 链式，RestTemplate 风 | Spring 开发者偏好 |
| 社区成熟度 | 53k stars，生产验证多 | 1.0 GA 稳定，快速增长 | 平手 |
| 版本号 | 0.x 仍在迭代 | 1.0+ SemVer 保证 | Spring AI 赢 |

## 项目为什么最终选了 Spring AI

原本选 LangChain4j 是看重"灵活 + 不劫持容器"。但迁移过程中发现：

### 1. 灵活度实际上差不多

**exclude 掉 Spring AI 自动配置后**，我们在 ChatModelFactory 里手动创建 Bean——跟 LangChain4j 的使用方式完全一样。Spring AI 的"管家"只是默认抢跑，关掉之后它也是个"工具库"。

### 2. 调用 API 更简洁

```java
// LangChain4j: 3 行
ChatRequest req = ChatRequest.builder().messages(UserMessage.from(text)).build();
String reply = model.chat(req).aiMessage().text();

// Spring AI: 1 行
String reply = model.call(text);
```

### 3. Spring 生态无缝集成

团队熟悉 Spring Boot。`ChatModel` 接口跟 `RestTemplate`、`WebClient` 风格一致，后续加 `@Retryable`、`@Async`、`@Cacheable` 注解都是原生支持。

### 4. 未来可直接用 VectorStore

如果项目以后要做 RAG，Spring AI 的 `PgVectorStore` / `RedisVectorStore` starter 一把梭——引依赖、配数据源、注入 `VectorStore` 就能用，LangChain4j 要自己拼。

### 5. 1.0 GA 后 API 稳定

SemVer 保证，不用担心小版本升了 breaking change。LangChain4j 还在 0.x 迭代。

## 一句话总结

**LangChain4j 给你完全的自由，但 API 风格比较"原始"；Spring AI 默认抢跑、要 exclude 才能自由，但一旦关掉自动配置，它的 API 更现代、跟 Spring 生态无缝。**

对于**我们这种多模型切换 + 需要工厂模式 + 想保持代码简洁**的场景：
- 灵活度上，exclude 之后 Spring AI 和 LangChain4j 没区别
- API 风格上，Spring AI 更简洁（`model.call(text)` 一行 vs 三行）
- 生态上，Spring AI 未来可接 VectorStore，LangChain4j 要自己拼

**迁移成本 = 改工厂 + Controller 里的 API 调用 + yml 加 exclude + base-url 去掉 `/v1`（大约半天）**。换来的是更好的 API 风格和未来生态扩展性。
