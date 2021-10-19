ARG jdkImage
ARG version

# Unzip in a separate container so that zip file layer is not part of final image
FROM ${jdkImage} as builder
ARG majorVersion
ARG version

ADD https://archive.apache.org/dist/tomcat/tomcat-${majorVersion}/v${version}/bin/apache-tomcat-${version}.tar.gz /server.tgz
RUN tar xf server.tgz && mv apache-tomcat-${version} /server && rm -rf /server/webapps && mkdir -p /server/webapps

FROM ${jdkImage}
COPY --from=builder /server /server

WORKDIR /server/bin
CMD /server/bin/catalina.sh run

COPY app.war /server/webapps/