ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:f51004008a2017ce3905fe7e1985d5aff62e596c4ab4111caad6d3cf33aa5cf1 as builder
ARG version

ADD https://archive.apache.org/dist/tomee/tomee-${version}/apache-tomee-${version}-webprofile.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/apache-tomee-webprofile-${version} /server
COPY app.war /server/webapps/
WORKDIR /server/bin
CMD /server/bin/catalina.bat run
