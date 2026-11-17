FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/out ./src/main/resources/static
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=api /api/target/playsense-0.1.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx350m -XX:MaxMetaspaceSize=128m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
