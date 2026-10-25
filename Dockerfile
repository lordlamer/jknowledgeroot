FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
# The pinned Temurin digest (17 October 2026) ships JRE 25.0.4.1+1 together with the
# Ubuntu glibc 2.39-0ubuntu8.9 and openssl 3.0.13-0ubuntu3.16 fixes; the earlier
# archive download and targeted package upgrades are therefore no longer needed.
RUN java -version
# Pages and attachments are rendered in the browser; the server does not render fonts.
# Remove the unused native XML parser together with its fontconfig consumers.
# Do not autoremove unrelated base-image dependencies.
RUN apt-get purge -y fontconfig libfontconfig1 libexpat1
RUN groupadd --gid 10001 knowledgeroot && useradd --uid 10001 --gid 10001 --no-create-home knowledgeroot \
    && mkdir -p /app /var/lib/knowledgeroot/files && chown -R 10001:10001 /app /var/lib/knowledgeroot
WORKDIR /app
ARG BUILD_VERSION=1.0.0-rc.3
ARG BUILD_REVISION=unknown
LABEL org.opencontainers.image.version=$BUILD_VERSION org.opencontainers.image.revision=$BUILD_REVISION
COPY --chown=10001:10001 target/*.jar /app/app.jar
COPY --chown=10001:10001 deploy/container-healthcheck.sh /app/healthcheck
ENV JAVA_VERSION=jdk-25.0.4.1+1 SPRING_PROFILES_ACTIVE=production KR_STORAGE_DRIVER=file KR_FILE_STORAGE_DIR=/var/lib/knowledgeroot/files
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 CMD ["bash", "/app/healthcheck"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
