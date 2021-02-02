ARG version
ARG jdk

FROM tomee:${jdk}-jre-${version}-webprofile

COPY app.war /usr/local/tomee/webapps/