ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:76cf422c98ca437b308374d0498280541fa42ac7061bb44015a6c8b70cf4db6a as builder
ARG version

ADD https://repo1.maven.org/maven2/org/apache/tomee/apache-tomee/${version}/apache-tomee-${version}-webprofile.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImageName}@sha256:${jdkImageHash}
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/apache-tomee-webprofile-${version} /server
COPY app.war /server/webapps/
WORKDIR /server/bin
CMD /server/bin/catalina.bat run
