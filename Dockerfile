FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:d120abd9d8d7dec94520ce974ece62d0e4eed8576eb00bbc84e6128307ab48ef
RUN groupadd --gid 10001 knowledgeroot && useradd --uid 10001 --gid 10001 --no-create-home knowledgeroot \
    && mkdir -p /app /var/lib/knowledgeroot/files && chown -R 10001:10001 /app /var/lib/knowledgeroot
WORKDIR /app
ARG BUILD_VERSION=1.0.0-rc.2
ARG BUILD_REVISION=unknown
LABEL org.opencontainers.image.version=$BUILD_VERSION org.opencontainers.image.revision=$BUILD_REVISION
COPY --chown=10001:10001 target/*.jar /app/app.jar
COPY --chown=10001:10001 deploy/container-healthcheck.sh /app/healthcheck
ENV SPRING_PROFILES_ACTIVE=production KR_STORAGE_DRIVER=file KR_FILE_STORAGE_DIR=/var/lib/knowledgeroot/files
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 CMD ["bash", "/app/healthcheck"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
