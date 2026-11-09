# Checklist

- [x] `pom.xml` 中已不再包含任何 `org.springframework.ai` 依赖
- [x] `pom.xml` 已包含 `io.agentscope:agentscope-core` 与 `io.agentscope:agentscope-extensions-model-openai`，版本为 `2.0.2`
- [x] `application.yml` 已删除 `spring.autoconfigure.exclude`，`chat.models` 三条配置保持不变
- [x] `ChatModelFactory` 返回 `io.agentscope.core.model.Model`，按配置的 base-url / api-key / model-name / temperature 构建
- [x] `ChatModelFactory` 对未知模型抛出 `IllegalArgumentException`，错误文案与原来一致
- [x] `ChatController.chat` 使用 `ReActAgent` + `Msg` 完成问答，回复取自 `Msg.getTextContent()`
- [x] `ChatController.vision` 使用 `DataBlock` + `Base64Source` 构造图片消息
- [x] 每次请求构建的 `ReActAgent` 都被关闭（try-with-resources），Model 实例走缓存复用
- [x] `/api/chat`、`/api/vision` 的请求/响应结构与错误文案未发生变化；`/api/models` 按 spec 要求移除了 `apiKey` 字段（`@JsonIgnore`）
- [x] `mvn -o compile` 通过
- [x] `mvn -o test` 通过（5 个测试），且测试不访问网络、不依赖真实模型
- [x] 启动应用后 `/api/models` 返回 3 个模型且不泄露 `apiKey`
- [x] `/api/chat` 指定 `remote-deepseek` 返回非空 reply
- [x] `/api/vision` 上传图片返回非空 reply
- [x] 实际请求地址确认为 `<base-url>/v1/chat/completions`：DeepSeek chat 与 vision 均调用成功；本机 Ollama 不可达，Ollama 场景未实测
- [x] `README.md` 技术栈描述已更新为 AgentScope，并补充后续 RAG / 记忆 / MCP 计划
- [x] `docs/` 下文档中与模型接入直接相关的 Spring AI 示例已改为 AgentScope 写法（历史选型对比章节保留并标注）
