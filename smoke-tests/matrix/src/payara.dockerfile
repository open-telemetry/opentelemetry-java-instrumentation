ARG version
ARG jdk

FROM payara/server-full:${version}${tagSuffix}

RUN rm ${PAYARA_DIR}/glassfish/modules/phonehome-bootstrap.jar

COPY app.war $DEPLOY_DIR