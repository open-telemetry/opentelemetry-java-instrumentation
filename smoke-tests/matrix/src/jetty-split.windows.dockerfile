ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:1809 as builder
ARG sourceVersion

ADD https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${sourceVersion}/jetty-home-${sourceVersion}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-1809
ARG sourceVersion

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/jetty-home-${sourceVersion} /server
RUN ["powershell", "-Command", "New-Item -Path / -Name base -ItemType directory"]
WORKDIR /base
ENV JETTY_HOME=/server
ENV JETTY_BASE=/base
RUN java -jar /server/start.jar --add-module=ext,server,jsp,resources,deploy,jstl,websocket,http
COPY app.war /base/webapps/
CMD java -jar /server/start.jar
