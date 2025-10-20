ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:418d8d0c6e026e5131e48f4d71ca66e9564c31b50f02b740235d32145a55c6ea as builder
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
