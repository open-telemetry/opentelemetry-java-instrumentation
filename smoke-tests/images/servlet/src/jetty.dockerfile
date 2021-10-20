ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM ${jdkImage} as builder
ARG sourceVersion

ADD https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${sourceVersion}/jetty-home-${sourceVersion}.tar.gz /server.tgz
RUN tar xf server.tgz && mv jetty-home-${sourceVersion} /server

FROM ${jdkImage}
COPY --from=builder /server /server
ENV JETTY_HOME=/server
ENV JETTY_BASE=/base
RUN mkdir $JETTY_BASE && \
  cd $JETTY_BASE && \
  # depending on Jetty version one of the following commands should succeed
  java -jar /server/start.jar --add-module=ext,server,jsp,resources,deploy,jstl,websocket,http || \
  java -jar /server/start.jar --add-to-start=ext,server,jsp,resources,deploy,jstl,websocket,http

WORKDIR $JETTY_BASE

CMD ["java","-jar","/server/start.jar"]

COPY app.war $JETTY_BASE/webapps/