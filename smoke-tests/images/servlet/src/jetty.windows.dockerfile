ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:3a2a2fdfbae2f720f6fe26f2d7680146712ce330f605b02a61d624889735c72e as builder
ARG sourceVersion

ADD https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${sourceVersion}/jetty-home-${sourceVersion}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImageName}@sha256:${jdkImageHash}
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
