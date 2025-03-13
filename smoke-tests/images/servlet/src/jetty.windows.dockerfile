ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:3cfddbce425e07cc658829098c29af86ff1a3ccc9b8d4735f5263a76ea4b7561 as builder
ARG sourceVersion

ADD https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${sourceVersion}/jetty-home-${sourceVersion}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImage}-windowsservercore-ltsc2022
ARG sourceVersion

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/jetty-home-${sourceVersion} /server
RUN ["powershell", "-Command", "New-Item -Path / -Name base -ItemType directory"]
ENV JETTY_HOME=/server
ENV JETTY_BASE=/base
WORKDIR $JETTY_BASE
# depending on Jetty version one of the following commands should succeed
RUN java -jar /server/start.jar --add-module=ext,server,jsp,resources,deploy,jstl,websocket,http; \
    if ($LASTEXITCODE -ne 0) { java -jar /server/start.jar --add-module=ext,server,ee10-jsp,resources,ee10-deploy,ee10-jstl,http; \
    if ($LASTEXITCODE -ne 0) { java -jar /server/start.jar --add-to-start=ext,server,jsp,resources,deploy,jstl,websocket,http }}

CMD java -jar /server/start.jar

COPY app.war $JETTY_BASE/webapps/
