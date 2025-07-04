ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:c489e1737a833a111f0f35b28257b1071d30b6db6b9ee50e88b7c08b901efc67 as builder
ARG majorVersion
ARG version

ADD https://archive.apache.org/dist/tomcat/tomcat-${majorVersion}/v${version}/bin/apache-tomcat-${version}-windows-x64.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-ltsc2022
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/apache-tomcat-${version} /server
# Delete default webapps to match the behavior of the official Linux Tomcat image
RUN ["powershell", "-Command", "Remove-Item -Recurse -Path /server/webapps"]
RUN ["powershell", "-Command", "New-Item -ItemType directory -Path /server/webapps"]
COPY app.war /server/webapps/
WORKDIR /server/bin
CMD /server/bin/catalina.bat run
