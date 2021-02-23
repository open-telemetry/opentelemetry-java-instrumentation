ARG version
ARG jdk
ARG vm

FROM tomee:${jdk}-jre-${version}-webprofile as default

FROM adoptopenjdk:${jdk}-jdk-${vm}

ENV SERVER_BASE=/usr/local/tomee
COPY --from=default $SERVER_BASE $SERVER_BASE
WORKDIR $SERVER_BASE

EXPOSE 8080
CMD ["bin/catalina.sh", "run"]
COPY app.war $SERVER_BASE/webapps/
