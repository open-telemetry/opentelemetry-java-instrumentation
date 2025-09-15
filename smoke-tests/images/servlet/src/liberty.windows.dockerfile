ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:92659de869382c14a0276a5e93215d88cb182dc22f1ff3ada1f1b68b8648f3b2 as builder
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
