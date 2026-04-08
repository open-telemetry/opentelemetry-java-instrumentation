ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:d4c6d1a8a1a306b12691c3b2e5e3a8bfad786cbd6b7831cd74a9a6a99eab08ad as builder
ARG majorVersion
ARG version

ADD https://archive.apache.org/dist/tomcat/tomcat-${majorVersion}/v${version}/bin/apache-tomcat-${version}-windows-x64.zip /server.zip
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
