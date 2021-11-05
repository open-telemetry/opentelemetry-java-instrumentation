ARG version

FROM ibmcom/websphere-traditional:${version}
ENV ENABLE_BASIC_LOGGING=true
COPY --chown=was:root app.war /work/app/
COPY --chown=was:root installApp.py /work/config/
COPY --chown=was:root changePort.py /work/config/
RUN /work/configure.sh

ENV EXTRACT_PORT_FROM_HOST_HEADER=false