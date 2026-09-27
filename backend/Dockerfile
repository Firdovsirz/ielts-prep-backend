# Build context: the repository root (prompts/ and data/ defaults are baked into the image).
#   docker build -f backend/Dockerfile -t ielts-prep-backend .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/ielts-prep-backend-*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 ielts \
    && useradd --system --uid 10001 --gid ielts --home-dir /app ielts \
    && mkdir -p /app /data \
    && chown ielts:ielts /data
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
COPY prompts /app/prompts
# Copied into an empty /data volume on first start (existing files are never overwritten).
COPY data/templates /app/defaults/templates
COPY data/descriptors /app/defaults/descriptors
COPY data/sources /app/defaults/sources
COPY data/seed /app/defaults/seed

ENV IELTS_DATA_DIR=/data \
    IELTS_PROMPTS_DIR=/app/prompts \
    IELTS_DEFAULTS_DIR=/app/defaults \
    SERVER_PORT=8090 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER ielts
VOLUME /data
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8090 && printf "GET /api/system/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
