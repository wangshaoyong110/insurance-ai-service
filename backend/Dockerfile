# ---------- 构建阶段 ----------
# 单独一层下载依赖：只要 pom 没变，改代码不会重新拉 Maven 仓库
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 国内直连 Maven Central 实测只有 ~160KB/s，全量依赖会拖到二十分钟以上；
# 阿里云镜像 ~12MB/s，差两个数量级。海外构建可覆盖为官方源。
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public
RUN if [ -n "$MAVEN_MIRROR" ]; then \
        mkdir -p /root/.m2 && \
        printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' \
            "$MAVEN_MIRROR" > /root/.m2/settings.xml; \
    fi

COPY backend/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -g 1001 -S app \
    && adduser -u 1001 -S app -G app \
    && mkdir -p /app/logs /app/upload \
    && chown -R app:app /app

COPY --from=build /build/target/insurance-ai-service.jar /app/app.jar

# 以非 root 运行：容器逃逸后的影响面直接降到最低
USER app

EXPOSE 8080

# 探活走 actuator，失败说明进程还在但业务线程可能已堵死
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
