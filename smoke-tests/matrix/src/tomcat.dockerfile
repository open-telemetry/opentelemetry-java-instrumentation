ARG version
ARG jdk

FROM tomcat:${version}-jdk${jdk}-adoptopenjdk-hotspot

COPY app.war /usr/local/tomcat/webapps/ROOT.war