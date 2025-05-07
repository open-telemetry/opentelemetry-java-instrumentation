ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:478a07c20826b51e632b0d9a17003b329d873f8f20aec7052e21281fd4e9fbd8 as builder
ARG version

ADD https://archive.apache.org/dist/tomee/tomee-${version}/apache-tomee-${version}-webprofile.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-ltsc2022
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/apache-tomee-webprofile-${version} /server
COPY app.war /server/webapps/
WORKDIR /server/bin
CMD /server/bin/catalina.bat run
