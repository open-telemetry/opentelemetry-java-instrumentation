FROM maven:3.9.9-eclipse-temurin-8@sha256:e20e4fc05fb5480c884a1f750c362ed0e91aca15cec5736e12796713710ec9b3 AS build
WORKDIR /work
COPY pom.xml .
COPY src src
ARG SPRING_VERSION=5.3.39
ARG COMMON_SPRING_VERSION=5.2.25.RELEASE
RUN export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")" \
    && mvn -B -ntp -Dspring.version=${SPRING_VERSION} package \
    && mvn -B -ntp -Dspring.version=${COMMON_SPRING_VERSION} dependency:copy-dependencies \
        -DincludeScope=runtime -DoutputDirectory=/common \
    && mkdir /agents \
    && for version in 1.33.0 2.10.0 2.33.0; do \
         curl -fsSL --retry 3 \
           "https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${version}/opentelemetry-javaagent-${version}.jar" \
           -o "/agents/${version}.jar" || exit 1; \
       done \
    && printf '%s\n' \
         '9addf06f79462041b8c4af5cd31645bcc02a34241c7250b4bdf4af9c51035f2b  /agents/1.33.0.jar' \
         'd05f6e36fac8db629263a6aaec2841cc934d064d7b19bfe38425b604b8b54926  /agents/2.10.0.jar' \
         '5f9d1f10056ed2b246fbb19543519b8066cda23d6aff19022dbdb84402d6c3ab  /agents/2.33.0.jar' \
       | sha256sum -c -
ARG LAYOUT=war
RUN mkdir /container-libs \
    && case "${LAYOUT}" in \
      war) ;; \
      common) mv target/probe/WEB-INF/lib/spring-*.jar /container-libs/ ;; \
      duplicate|parent-first) cp /common/spring-*.jar /container-libs/ ;; \
      *) echo "Unknown LAYOUT: ${LAYOUT}" >&2; exit 1 ;; \
    esac \
    && export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")" \
    && jar cf target/deploy.war -C target/probe . \
    && mkdir -p target/shared/WEB-INF \
    && cp -r target/probe/WEB-INF/classes target/shared/WEB-INF/ \
    && cp target/probe/WEB-INF/web.xml target/probe/WEB-INF/servlet.xml target/shared/WEB-INF/ \
    && cp target/probe/WEB-INF/path-patterns.xml target/shared/WEB-INF/root.xml \
    && jar cf target/shared.war -C target/shared .

FROM tomcat:9.0.68-jdk8-temurin@sha256:096fdea4ddcb7a273c8b558880a314bb9d6987f3532e72683084a38873b6875e
COPY --from=build /work/target/deploy.war /usr/local/tomcat/webapps/probe.war
COPY --from=build /container-libs/ /usr/local/tomcat/lib/
COPY --from=build /agents /opt/agents
COPY --from=build /work/target/shared.war /opt/shared.war
ARG LAYOUT=war
RUN if [ "${LAYOUT}" = parent-first ]; then \
      mkdir -p conf/Catalina/localhost \
      && printf '%s\n' '<Context><Loader delegate="true"/></Context>' > conf/Catalina/localhost/probe.xml; \
    fi
ENV OTEL_TRACES_EXPORTER=none \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none \
    OTEL_SERVICE_NAME=spring-war-compatibility
