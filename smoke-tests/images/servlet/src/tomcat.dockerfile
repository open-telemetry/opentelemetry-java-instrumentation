ARG version
ARG jdk
ARG tagSuffix

FROM tomcat:${version}-jdk${jdk}${tagSuffix}

COPY app.war /usr/local/tomcat/webapps/