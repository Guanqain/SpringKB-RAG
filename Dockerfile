# =========================================================
# SpringKB-RAG 应用容器化（多阶段构建）
# 阶段 1：Node 构建前端（Vue 3 + Vite）
# 阶段 2：Maven 构建后端，前端产物复制进 static/（一体化托管）
# 阶段 3：JRE 运行
#
# 说明：基础镜像使用 dockerproxy.net 前缀（国内网络拉取 Docker Hub
# 官方镜像会超时）。该源为标准 Docker Hub 代理，在任意网络下均可用；
# 若环境已配置 registry mirror，可去掉此前缀。
# =========================================================

# ---------- 阶段 1：前端构建 ----------
FROM dockerproxy.net/library/node:20-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：后端构建（跳过测试编译，测试在 CI/本地执行） ----------
FROM dockerproxy.net/library/maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn package -Dmaven.test.skip=true -B

# ---------- 阶段 3：运行 ----------
FROM dockerproxy.net/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
# 前端页面已打入 jar（static/index.html），端口 8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
