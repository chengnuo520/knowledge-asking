# Spring AI Chat — 多模型问答 + 图片识别 Web 项目

基于 **Spring Boot + Spring AI** 实现的知识问答 Web 应用。
配置驱动多模型切换（远程 DeepSeek / 本地 Ollama），支持图片识别（Vision）。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.3.5 |
| AI 框架 | Spring AI 1.0.3（OpenAI 兼容协议） |
| 前端     | 原生 HTML / CSS / JavaScript（无构建依赖） |
| 容器化   | Docker + docker-compose |
| 基础镜像 | `maven:3.9.9-eclipse-temurin-17`（构建） / `eclipse-temurin:17-jre-alpine`（运行） |

## 功能

- ✅ 多模型切换（远程 DeepSeek + 本地 Ollama，配置驱动，零代码加模型）
- ✅ 图片识别 Vision（Qwen3-VL-4B + OpenAI 兼容端点）
- ✅ 简洁聊天 UI（侧边栏模型下拉框 + 输入框 📎 上传图片）

## 当前模型配置

| Model ID | 显示名 | 来源 | 支持 Vision |
|---|---|---|---|
| `remote-deepseek` | DeepSeek Flash (远程) | api.deepseek.com | ✅ |
| `ollama-deepseek-r1` | DeepSeek R1 (本地/Ollama) | localhost:11434 | ❌ |
| `ollama-qwen3-vl` | Qwen3-VL-4B (本地/Ollama) | localhost:11434 | ✅ |

> 所有模型都走 **OpenAI 兼容协议**，所以一个 Spring AI starter 覆盖全部。
> 详见 [docs/01-模型切换机制.md](docs/01-模型切换机制.md)。

## 项目结构

```
.
├── Dockerfile                      # 多阶段镜像构建
├── docker-compose.yml              # 容器编排
├── .env.example                    # 环境变量模板
├── .dockerignore
├── pom.xml                         # Maven 依赖 (spring-ai-starter-model-openai + ollama)
├── README.md
├── docs/                           # 功能点学习文档
│   ├── 01-模型切换机制.md
│   ├── 02-图片识别Vision.md
│   ├── 03-Docker部署.md
│   └── 04-迁移到SpringAI.md
└── src/main/
    ├── java/com/example/chat/
    │   ├── DemoApplication.java       # Spring Boot 主入口
    │   ├── ChatConfig.java           # @ConfigurationProperties 绑定模型列表
    │   ├── ChatModelFactory.java     # 工厂 + ConcurrentHashMap 缓存
    │   ├── ChatController.java       # /api/chat + /api/vision + /api/models
    │   └── ModelDefinition.java      # 模型配置 POJO
    └── resources/
        ├── application.yml           # chat.models 列表定义 + exclude 自动配置
        └── static/
            └── index.html            # 前端页面（下拉框 + 📎 图片上传）
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT`       | `8080` | 服务监听端口 |
| `DEEPSEEK_API_KEY`   | — (必填) | DeepSeek 平台 API Key |
| `OLLAMA_BASE_URL`   | `http://localhost:11434` | Ollama 地址（容器里自动用 `host.docker.internal`） |

> 完整模型配置见 `application.yml` 的 `chat.models` 数组，可自由增删。

## 快速开始（Docker）

```bash
# 1. 复制并编辑环境变量
cp .env.example .env
# 填入 DEEPSEEK_API_KEY

# 2. 构建并启动
docker compose up -d --build

# 3. 查看日志
docker compose logs -f
```

启动成功后浏览器访问：<http://localhost:8080>

## 本地开发（非 Docker）

需要 JDK 17+ 和 Maven 3.9+，以及**本地运行的 Ollama**（可选，只想用远程 DeepSeek 则不需要）。

```bash
# 1. 设置 API Key（Windows PowerShell）
$env:DEEPSEEK_API_KEY="your-key-here"

# 2. 本地 Ollama 拉模型（可选）
ollama pull deepseek-r1:latest
ollama pull modelscope.cn/Qwen/Qwen3-VL-4B-Instruct-GGUF:latest

# 3. 编译并运行
mvn spring-boot:run
```

访问：<http://localhost:8080>

## API 接口

### GET `/api/models`

返回可用模型列表（**不暴露 apiKey/baseUrl**）：

```json
[
  {"id":"remote-deepseek","name":"DeepSeek Flash (远程)","modelName":"deepseek-flash"},
  {"id":"ollama-deepseek-r1","name":"DeepSeek R1 (本地/Ollama)","modelName":"deepseek-r1:latest"}
]
```

### POST `/api/chat`

纯文本对话：

```json
// 请求
{ "message": "你好", "model": "remote-deepseek" }

// 成功
{ "reply": "你好！我是 DeepSeek..." }

// 错误
{ "error": "模型调用失败: ..." }
```

### POST `/api/vision`

图片识别（`multipart/form-data`）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `image` | MultipartFile | 图片文件 |
| `message` | String | 问题，默认"描述这张图片" |
| `model` | String | 模型 ID，默认 `ollama-qwen3-vl` |

```bash
curl -F "image=@test.png" -F "message=这张图是什么颜色" -F "model=ollama-qwen3-vl" \
     http://localhost:8080/api/vision
```

## 加新模型（零代码）

只需两步，不用改 Java：

**1.** 在 `application.yml` 的 `chat.models` 下加一条：

```yaml
    - id: remote-qwen
      name: "Qwen Plus (阿里云)"
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${DASHSCOPE_API_KEY:}
      model-name: qwen-plus
      temperature: 0.7
```

**2.** Docker 部署则在 `docker-compose.yml` 的 `environment` 加对应变量，重启容器即可。

详见 [docs/01-模型切换机制.md](docs/01-模型切换机制.md)。

## 注意事项

- Spring AI 默认会自动装配 `OpenAiChatModel` Bean，我们在 `application.yml` 里 `spring.autoconfigure.exclude` 关掉了它，改为工厂手动创建——这样才能支持多模型动态切换。
- Spring AI 的 base-url **不带** `/v1`（如 `https://api.deepseek.com`），内部会自动拼 `/v1/chat/completions`。LangChain4j 时期你写的完整 URL 要注意去掉 `/v1`。
- 本项目仅保证**基础问答 + 图片识别能力**，不含生产环境优化、权限校验、多轮对话记忆、流式输出等扩展功能。
