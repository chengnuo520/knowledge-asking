# Docker 部署

## 两个文件

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 两阶段构建，先编译再运行 |
| `docker-compose.yml` | 编排 + 环境变量注入 + 端口映射 |

## Dockerfile：两阶段构建

```dockerfile
# ========== 阶段 1: 编译 ==========
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline        # 先下载依赖, 利用 Docker 缓存
COPY src ./src
RUN mvn package -DskipTests -q

# ========== 阶段 2: 运行 ==========
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**为什么两阶段？**
- `maven:3.9.9` 镜像 500MB+，`alpine` JRE 只有 180MB
- 最终镜像里没有 Maven、没有源码，攻击面小

**pom.xml 里要配**：
```xml
<build>
    <finalName>app</finalName>   <!-- jar 名 = app.jar, 和 Dockerfile COPY 对得上 -->
</build>
```

## docker-compose.yml

```yaml
services:
  deepseek-chat:
    build: .
    image: deepseek-chat:latest
    container_name: deepseek-chat
    restart: unless-stopped
    ports:
      - "8083:8080"
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      # 注意: 不带 /v1! Spring AI 内部自动拼
      - OLLAMA_BASE_URL=${OLLAMA_BASE_URL:-http://host.docker.internal:11434}
```

**`host.docker.internal` 是什么？**

| 运行场景 | Ollama base-url | 说明 |
|---|---|---|
| 本地 `mvn spring-boot:run` | `http://localhost:11434` | yml 默认值（Spring AI 自动拼 `/v1/chat/completions`） |
| Docker Desktop (Win/Mac) | `http://host.docker.internal:11434` | compose 覆盖 |
| Linux 原生 Docker | 同上，但要 `--add-host host.docker.internal:host-gateway` | |

## Spring AI vs LangChain4j 在 Docker 部署的区别

唯一的差异就是 **base-url 不带 `/v1`**：

```yaml
# 以前 LangChain4j 写法:
OLLAMA_BASE_URL: http://host.docker.internal:11434/v1
DEEPSEEK_BASE_URL: https://api.deepseek.com/v1

# 现在 Spring AI 写法:
OLLAMA_BASE_URL: http://host.docker.internal:11434
DEEPSEEK_BASE_URL: https://api.deepseek.com
```

其他完全不变。

## 启动三步

```bash
# 1. 填 API Key
cp .env.example .env
# 编辑 .env, 把 DEEPSEEK_API_KEY 改成你真实的 key

# 2. 构建 + 启动
docker compose up -d --build

# 3. 看日志确认启动
docker compose logs -f

# 4. 访问 http://localhost:8083
```

## 常用命令

```bash
docker compose up -d --build               # 构建 + 后台启动
docker compose up -d --force-recreate      # 改了 .env 后强制重建容器
docker compose down                        # 停 + 删容器
docker compose logs -f                     # 看实时日志
docker compose ps                          # 看状态
docker exec -it deepseek-chat sh           # 进容器
```

## 常见问题

| 问题 | 原因 | 解法 |
|---|---|---|
| 启动报 `API key must be set` | Spring AI 自动配置抢跑 | yml 里 exclude 掉 AI 自动配置类 |
| API 404 | base-url 多写了 `/v1`，Spring AI 又拼一次 → `/v1/v1/...` | base-url 只写根地址 |
| Ollama 连不上 | Windows Docker 里用了 `localhost` 而不是 `host.docker.internal` | compose 里覆盖 OLLAMA_BASE_URL |
| API Key 没生效 | 单纯 restart 不会重新读环境变量 | `--force-recreate` |
| MimeType 报错 `Invalid token character '/'` | 用了 `new MimeType("image/png")` | 用 `MimeTypeUtils.parseMimeType()` |
