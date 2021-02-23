ARG version
ARG jdk
ARG vm

FROM tomcat:${version}-jdk${jdk}-adoptopenjdk-${vm}

COPY app.war /usr/local/tomcat/webapps/