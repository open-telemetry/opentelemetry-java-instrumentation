ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:d9e1a220c13cf25c7b213fbd96df2b63671e2dba0de3909003d4bb23a8bc8a1c as builder
ARG version
ARG baseDownloadUrl

ADD ${baseDownloadUrl}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-ltsc2022
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/wildfly-${version} /server
COPY app.war /server/standalone/deployments/
WORKDIR /server/bin
CMD /server/bin/standalone.bat -b 0.0.0.0
