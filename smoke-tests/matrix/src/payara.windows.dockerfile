ARG jdkImage

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:1809 as builder
ARG version

ADD https://s3-eu-west-1.amazonaws.com/payara.fish/Payara+Downloads/${version}/payara-${version}.zip /server.zip
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]
RUN ["powershell", "-Command", "remove-item -Path /server/payara5/glassfish/modules/phonehome-bootstrap.jar"]

FROM ${jdkImage}-windowsservercore-1809

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/payara5 /server
COPY app.war /server/glassfish/domains/domain1/autodeploy/
COPY launch.bat /server/
WORKDIR /server
CMD /server/launch.bat
