# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml .
COPY common/pom.xml common/
COPY master/pom.xml master/
COPY storage-node/pom.xml storage-node/

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -B -pl common,storage-node -am

# Copy source code
COPY common/src common/src
COPY storage-node/src storage-node/src

# Build
RUN mvn package -B -pl common,storage-node -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S dfs && adduser -S dfs -G dfs
RUN mkdir -p /data/chunks && chown -R dfs:dfs /data
COPY --from=build /app/storage-node/target/*.jar app.jar
RUN chown -R dfs:dfs /app

USER dfs

EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]
