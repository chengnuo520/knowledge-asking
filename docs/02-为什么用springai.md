# LangChain4j vs Spring AI — 选型对比

> 说明：本文记录项目从 LangChain4j 迁到 Spring AI 时的选型思考。此后项目已完成向 **AgentScope Java 2.0.2** 的迁移，模型接入与问答调用以 AgentScope 写法为准（`OpenAIChatModel` + `ReActAgent`），下文相关代码示例已同步更新。

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

**AgentScope Java** — 同样是单层 builder，连接信息和模型名一次给全：

```java
OpenAIChatModel model = OpenAIChatModel.builder()
    .apiKey("sk-xxx")
    .baseUrl("https://api.deepseek.com/v1")   // 完整 OpenAI 兼容端点
    .modelName("deepseek-flash")
    .stream(false)
    .build();
```

AgentScope 不再有 Spring AI 的 `OpenAiApi` / `OpenAiChatOptions` 分层，`OpenAIChatModel` 只负责模型调用；要执行问答还要再包一层 `ReActAgent`。温度等生成参数在 Agent 调用时传入。

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

**AgentScope Java**：

```java
// 简单聊天 — 先建 ReActAgent，再把用户消息包成 Msg
try (ReActAgent agent = ReActAgent.builder().name("assistant").model(model).build()) {
    Msg msg = Msg.builder().role(MsgRole.USER).textContent("你好").build();
    String reply = agent.call(msg).block().getTextContent();
}

// Vision — 图片转 Base64 后装进 DataBlock
DataBlock imageBlock = DataBlock.builder()
    .source(Base64Source.builder()
        .data(Base64.getEncoder().encodeToString(file.getBytes()))
        .mediaType(contentType)
        .build())
    .build();
Msg msg = Msg.builder().role(MsgRole.USER).textContent("描述这张图").content(imageBlock).build();
```

**AgentScope 的一次问答要经过 `Msg` + `ReActAgent`**，比 Spring AI 的 `model.call(String)` 多几行，但换来 ReAct 推理循环、统一消息模型和后续工具调用能力。Vision 也不再自动编码，需要显式 `Base64Source`。

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

> 迁移到 AgentScope 后这一节不再是现状：AgentScope 不参与 Spring Boot 自动装配，`OpenAIChatModel` 全部在 `ChatModelFactory` 里显式创建，不需要 exclude 任何自动配置类。

## base-url 规则不同

| | LangChain4j | Spring AI |
|---|---|---|
| 期望格式 | `https://api.deepseek.com/v1` | `https://api.deepseek.com` |
| 拼 URL | `baseUrl + "/chat/completions"` | `baseUrl + "/v1/chat/completions"` |
| 写 yml 时 | 完整 URL | 根地址，不带 `/v1` |

**迁移时最容易忘这一条**。LangChain4j 时代你写的 `/v1` 到 Spring AI 会变成 `/v1/v1/chat/completions` → 404。

迁移到 AgentScope 后规则又变了一次：`OpenAIChatModel` 的 `baseUrl` 要指向完整的 OpenAI 兼容端点（通常带 `/v1`），框架不会自动拼接。

## 完整对比表（LangChain4j vs Spring AI，历史选型）

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

## 项目为什么最终选了 AgentScope Java

早期选型在 LangChain4j 和 Spring AI 之间反复比较，最终项目落到 AgentScope Java 2.0.2，原因有三点：

### 1. 多模型接入依然是一个 builder

`agentscope-extensions-model-openai` 的 `OpenAIChatModel` 用一层 builder 收下 `apiKey` / `baseUrl` / `modelName`，工厂模式照旧，配置驱动切换多模型的写法不用推翻。

### 2. 问答统一走 ReActAgent

模型只负责一次生成，`ReActAgent` 负责推理循环和后续的工具调用。比起直接调用 `ChatModel`，它给项目留出了扩展空间：后续接 MCP 工具、长期记忆、知识库检索都可以复用同一套 Agent 调用方式。

### 3. 后续能力有原生支持

AgentScope 自带 `Knowledge`、`LongTermMemory` 和 MCP 支持，正好对应项目后续的 RAG 知识库、多轮长期记忆和工具接入计划，不需要再换框架或自己拼装。

Spring Boot 依然是 Web 层和依赖注入的基础，AgentScope 只作为 AI 框架使用，不接管容器。

## 一句话总结

**选型的重点不只是谁的调用 API 更短，而是谁能承接后续的 Agent 能力。AgentScope 用一层 `OpenAIChatModel` builder 保住多模型工厂的简洁，再用 `ReActAgent` 把工具、记忆、知识库这些能力串起来。**
