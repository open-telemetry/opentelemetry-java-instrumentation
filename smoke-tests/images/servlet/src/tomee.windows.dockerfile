ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:3a2a2fdfbae2f720f6fe26f2d7680146712ce330f605b02a61d624889735c72e as builder
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
