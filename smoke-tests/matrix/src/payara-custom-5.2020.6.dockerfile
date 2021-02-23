ARG version
ARG jdk
ARG vm

FROM payara/server-full:${version} as default
ENV HOME_DIR=$HOME_DIR

FROM adoptopenjdk:${jdk}-jdk-${vm}

# These environment variables have been confirmed to work with 5.2020.6 only
ENV HOME_DIR=/opt/payara
ENV PAYARA_DIR="${HOME_DIR}/appserver" \
    SCRIPT_DIR="${HOME_DIR}/scripts" \
    CONFIG_DIR="${HOME_DIR}/config" \
    DEPLOY_DIR="${HOME_DIR}/deployments" \
    PASSWORD_FILE="${HOME_DIR}/passwordFile" \
    ADMIN_USER="admin" \
    ADMIN_PASSWORD="admin" \
    MEM_MAX_RAM_PERCENTAGE=70.0 \
    MEM_XSS=512k \
    DOMAIN_NAME="production" \
    PREBOOT_COMMANDS="${HOME_DIR}/config/pre-boot-commands.asadmin" \
    POSTBOOT_COMMANDS="${HOME_DIR}/config/post-boot-commands.asadmin" \
    PATH="${PATH}:${HOME_DIR}/scripts"

COPY --from=default $HOME_DIR $HOME_DIR
RUN rm ${PAYARA_DIR}/glassfish/modules/phonehome-bootstrap.jar

WORKDIR $HOME_DIR

EXPOSE 8080
CMD ["entrypoint.sh"]
COPY app.war $DEPLOY_DIR
