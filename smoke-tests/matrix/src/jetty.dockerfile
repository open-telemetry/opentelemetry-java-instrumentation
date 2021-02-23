ARG version
ARG jdk
ARG vm
FROM jetty:${version}-jre11-slim as jetty

FROM adoptopenjdk:${jdk}-jdk-${vm}
ENV JETTY_HOME /usr/local/jetty
ENV JETTY_BASE /var/lib/jetty
ENV TMPDIR /tmp/jetty
ENV PATH $JETTY_HOME/bin:$PATH

COPY --from=jetty $JETTY_HOME $JETTY_HOME
COPY --from=jetty $JETTY_BASE $JETTY_BASE
COPY --from=jetty $TMPDIR $TMPDIR

WORKDIR $JETTY_BASE
COPY --from=jetty docker-entrypoint.sh generate-jetty-start.sh /

COPY app.war $JETTY_BASE/webapps/

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java","-jar","/usr/local/jetty/start.jar"]