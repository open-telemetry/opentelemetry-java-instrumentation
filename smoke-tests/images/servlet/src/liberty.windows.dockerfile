ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:c489e1737a833a111f0f35b28257b1071d30b6db6b9ee50e88b7c08b901efc67 as builder
ARG version
ARG release

ADD https://public.dhe.ibm.com/ibmdl/export/pub/software/openliberty/runtime/release/${release}/openliberty-${version}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-ltsc2022

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/wlp /server
COPY server.xml /server/usr/servers/defaultServer/
COPY app.war /server/usr/servers/defaultServer/apps/

WORKDIR /server/bin
CMD /server/bin/server.bat run defaultServer
