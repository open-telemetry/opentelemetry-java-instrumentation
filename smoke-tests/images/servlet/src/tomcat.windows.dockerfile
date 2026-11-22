ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:f1a914079973258061ddbc95a9af579eab2ee252a6fddb254b1be5d769761acd as builder

COPY server.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImageName}@sha256:${jdkImageHash}
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/apache-tomcat-${version} /server
# Delete default webapps to match the behavior of the official Linux Tomcat image
RUN ["powershell", "-Command", "Remove-Item -Recurse -Path /server/webapps"]
RUN ["powershell", "-Command", "New-Item -ItemType directory -Path /server/webapps"]
COPY app.war /server/webapps/
WORKDIR /server/bin
CMD /server/bin/catalina.bat run
