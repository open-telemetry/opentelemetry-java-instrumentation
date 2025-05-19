ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:a9e475f9925792421937e65bd191d79f97578e3013865b0b71f4fadee0d16d09 as builder
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
