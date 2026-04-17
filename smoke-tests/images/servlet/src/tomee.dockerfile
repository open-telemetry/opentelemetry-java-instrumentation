ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM ${jdkImageName}@sha256:${jdkImageHash} as builder
ARG version

ADD https://repo1.maven.org/maven2/org/apache/tomee/apache-tomee/${version}/apache-tomee-${version}-webprofile.tar.gz /server.tgz
RUN tar xf server.tgz && ls -al / && mv apache-tomee-webprofile-${version} /server

FROM ${jdkImageName}@sha256:${jdkImageHash}
COPY --from=builder /server /server

WORKDIR /server/bin
CMD /server/bin/catalina.sh run

COPY app.war /server/webapps/
