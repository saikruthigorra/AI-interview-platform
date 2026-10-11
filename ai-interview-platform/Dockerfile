# ─────────────────────────────────────────────────────────────────────────────
# InterviewIQ — one image, one port: the React app is built and baked into the
# Spring Boot jar, which serves both the website and the API. The database is
# external (TiDB Cloud Serverless) — nothing stateful lives in this image.
#
#   docker build -t interviewiq .
#   docker run -p 8080:8080 --env-file .env.local interviewiq
#
# Ports: Render (and most PaaS) inject PORT and forward traffic there. The app
# resolves SERVER_PORT → PORT → 8080 in that order, so we deliberately do NOT
# bake a port here — the container binds whatever the platform asks for and
# Render's port detection succeeds instantly. EXPOSE is documentation only.
# ─────────────────────────────────────────────────────────────────────────────

# ── stage 1: build the React app ────────────────────────────────────────────
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ── stage 2: build the jar with the website inside it ───────────────────────
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /build
COPY backend/pom.xml .
# Warm the dependency cache (layer only invalidates when the pom changes).
# Non-fatal: anything it misses is fetched again by the package step below.
RUN mvn -q -B dependency:go-offline || true
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# ── stage 3: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /build/target/interviewiq-api-*.jar app.jar

# Render's free tier gives 512 MB — leave headroom for metaspace, threads and
# direct buffers. Override by setting JAVA_TOOL_OPTIONS on the service.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
