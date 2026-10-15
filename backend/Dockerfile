# ---------- 构建层：Maven + JDK 17 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先单独拷贝 pom 拉依赖：pom 不变时命中 Docker 层缓存，免重复下载
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- 运行层：仅 JRE ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 消息时间展示统一东八区
ENV TZ=Asia/Shanghai

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8091
# MaxRAMPercentage 让 JVM 自适应容器内存限额
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
