FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:d120abd9d8d7dec94520ce974ece62d0e4eed8576eb00bbc84e6128307ab48ef AS runtime-download
# The official container tag lags the Temurin security release. Replace the whole
# JRE with the immutable upstream archive; never combine files from two versions.
ARG TARGETARCH
RUN set -eux; apt-get update; apt-get install -y --no-install-recommends curl; \
    case "$TARGETARCH" in \
      amd64) arch=x64; checksum=1731a34baadec5479258ea0202e4d5d865d2efeee60cb0c7d7eb056fe96ca219 ;; \
      arm64) arch=aarch64; checksum=34828cbb93ed31c281c84ecb31ddab655d11a802f263c1fc019d42e9e0230fed ;; \
      *) echo "Unsupported JRE architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    curl --fail --location --retry 3 --proto '=https' --proto-redir '=https' \
      "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_${arch}_linux_hotspot_25.0.4.1_1.tar.gz" -o /tmp/jre.tar.gz; \
    echo "$checksum  /tmp/jre.tar.gz" | sha256sum --check --strict; \
    mkdir /tmp/updated-jre; \
    tar --extract --gzip --file /tmp/jre.tar.gz --directory /tmp/updated-jre --strip-components=1 --no-same-owner; \
    rm /tmp/jre.tar.gz; \
    grep -Fx 'JAVA_RUNTIME_VERSION="25.0.4.1+1-LTS"' /tmp/updated-jre/release

FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:d120abd9d8d7dec94520ce974ece62d0e4eed8576eb00bbc84e6128307ab48ef
RUN rm -rf /opt/java/openjdk
COPY --from=runtime-download /tmp/updated-jre /opt/java/openjdk
RUN java -version
# Ubuntu USN-8737: the pinned Temurin image still contains glibc 2.39-0ubuntu8.8.
# Remove this layer once a verified Temurin digest includes these fixes.
RUN apt-get update && apt-get install -y --no-install-recommends --only-upgrade \
        libc6=2.39-0ubuntu8.9 libc-bin=2.39-0ubuntu8.9 locales=2.39-0ubuntu8.9 \
    && rm -rf /var/lib/apt/lists/*
# Pages and attachments are rendered in the browser; the server does not render fonts.
# Remove the unused native XML parser together with its fontconfig consumers.
# Do not autoremove unrelated base-image dependencies.
RUN apt-get purge -y fontconfig libfontconfig1 libexpat1
RUN groupadd --gid 10001 knowledgeroot && useradd --uid 10001 --gid 10001 --no-create-home knowledgeroot \
    && mkdir -p /app /var/lib/knowledgeroot/files && chown -R 10001:10001 /app /var/lib/knowledgeroot
WORKDIR /app
ARG BUILD_VERSION=1.0.0-rc.2
ARG BUILD_REVISION=unknown
LABEL org.opencontainers.image.version=$BUILD_VERSION org.opencontainers.image.revision=$BUILD_REVISION
COPY --chown=10001:10001 target/*.jar /app/app.jar
COPY --chown=10001:10001 deploy/container-healthcheck.sh /app/healthcheck
ENV JAVA_VERSION=jdk-25.0.4.1+1 SPRING_PROFILES_ACTIVE=production KR_STORAGE_DRIVER=file KR_FILE_STORAGE_DIR=/var/lib/knowledgeroot/files
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 CMD ["bash", "/app/healthcheck"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
