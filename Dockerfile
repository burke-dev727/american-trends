# Builds one image serving the React frontend and the Java API from a data snapshot.
# Create snapshot.json first:
#   cd server && ./gradlew exportSnapshot -Psnapshot=../snapshot.json

FROM node:24-alpine AS web
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.ts tsconfig.json tsconfig.app.json tsconfig.node.json ./
COPY public ./public
COPY src ./src
# Donate link is compiled into the static pages. Pass it with --build-arg.
ARG VITE_PAYPAL_DONATE_URL
ENV VITE_PAYPAL_DONATE_URL=$VITE_PAYPAL_DONATE_URL
RUN npm run build

FROM eclipse-temurin:17-jdk-jammy AS server
WORKDIR /src
COPY server/gradlew server/gradlew.bat server/settings.gradle server/build.gradle server/gradle.properties ./
COPY server/gradle ./gradle
COPY server/src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system --gid 65532 nonroot \
    && useradd --system --uid 65532 --gid nonroot --home-dir /app nonroot
WORKDIR /app
COPY --from=server /src/build/libs/energy-api.jar ./energy-api.jar
COPY --from=web /app/dist ./dist
COPY --chmod=0644 snapshot.json ./snapshot.json
ENV DATA_SOURCE=snapshot \
    SNAPSHOT_PATH=/app/snapshot.json \
    STATIC_DIR=/app/dist \
    TRUST_PROXY_HEADERS=true \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "/app/energy-api.jar"]
