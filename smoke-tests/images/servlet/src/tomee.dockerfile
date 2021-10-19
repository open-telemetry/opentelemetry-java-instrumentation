ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM ${jdkImage} as builder
ARG version

ADD https://archive.apache.org/dist/tomee/tomee-${version}/apache-tomee-${version}-webprofile.tar.gz /server.tgz
RUN tar xf server.tgz && ls -al / && mv apache-tomee-webprofile-${version} /server

FROM ${jdkImage}
COPY --from=builder /server /server

WORKDIR /server/bin
CMD /server/bin/catalina.sh run

COPY app.war /server/webapps/