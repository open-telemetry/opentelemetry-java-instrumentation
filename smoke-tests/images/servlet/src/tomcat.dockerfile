ARG jdkImageName
ARG jdkImageHash
ARG version

# Unzip in a separate container so that zip file layer is not part of final image
FROM ${jdkImageName}@sha256:${jdkImageHash} as builder
ARG version

COPY server.tgz /server.tgz
RUN tar xf server.tgz && mv apache-tomcat-${version} /server && rm -rf /server/webapps && mkdir -p /server/webapps

FROM ${jdkImageName}@sha256:${jdkImageHash}
COPY --from=builder /server /server

WORKDIR /server/bin
CMD /server/bin/catalina.sh run

COPY app.war /server/webapps/
