ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:a264df8cd8c329eed3fd1e0cafcd4f3dc453e2c72a277f9bb140fd6f10a2eefc as builder
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
