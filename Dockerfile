# 运行时镜像：只装宿主机构建好的 jar
#
# 为什么要先在宿主机构建：前端（Vue3）需要 Node，而 Java 构建镜像里没有 npm；
# 在 Dockerfile 里再塞一个 Node 阶段会让镜像构建多下载 50MB+ 且拉长构建时间。
# 因此把「前端 + 后端」的构建交给宿主机，镜像只负责运行。
#
# 正确用法：
#   mvn -Pwith-frontend package          # 编译后端 + 构建 Vue 到 static/app + 打 jar
#   docker compose up -d --build         # 本文件只 COPY 产物
#
# 注意：.dockerignore 里放行了 target/app.jar，其余 target/ 内容不会进构建上下文。

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/app.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
