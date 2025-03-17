ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:b1e1bd182c21723af77037208115bdc387dcecbab3802ed243d572a76ac50853 as builder
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
