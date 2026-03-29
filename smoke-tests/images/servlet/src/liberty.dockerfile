ARG jdkImageName
ARG jdkImageHash
ARG imageName
ARG imageHash

FROM ${imageName}@sha256:${imageHash} as liberty

FROM ${jdkImageName}@sha256:${jdkImageHash}

ENV CONFIG /config
ENV LIBERTY /opt/ol
ENV PATH=/opt/ol/wlp/bin:/opt/ol/docker/:/opt/ol/helpers/build:$PATH \
    LOG_DIR=/logs \
    WLP_OUTPUT_DIR=/opt/ol/wlp/output \
    WLP_SKIP_MAXPERMSIZE=true

COPY --from=liberty $LIBERTY $LIBERTY
RUN ln -s /opt/ol/wlp/usr/servers/defaultServer /config

COPY --chown=1001:0 server.xml /config/server.xml
COPY --chown=1001:0 app.war /config/apps/
RUN configure.sh

EXPOSE 8080

ENTRYPOINT ["/opt/ol/helpers/runtime/docker-server.sh"]
CMD ["/opt/ol/wlp/bin/server", "run", "defaultServer"]
