ARG jdkImageName
ARG jdkImageHash

# Unzip in a separate container so that zip file layer is not part of final image
FROM mcr.microsoft.com/windows/servercore:ltsc2022@sha256:d4c6d1a8a1a306b12691c3b2e5e3a8bfad786cbd6b7831cd74a9a6a99eab08ad as builder
ARG version
ARG baseDownloadUrl

RUN ["powershell", "-Command", "$retries = 5; $wait = 3; $url = $env:baseDownloadUrl + '.zip'; for ($i = 1; $i -le $retries; $i++) { try { Invoke-WebRequest -Uri $url -OutFile /server.zip -UseBasicParsing; break } catch { if ($i -eq $retries) { throw } else { Write-Host \"Download attempt $i failed, retrying in $wait seconds...\"; Start-Sleep -Seconds $wait } } }"]
RUN ["powershell", "-Command", "expand-archive -Path /server.zip -DestinationPath /server"]

FROM ${jdkImageName}@sha256:${jdkImageHash}
ARG version

# Make /server the base directory to simplify all further paths
COPY --from=builder /server/wildfly-${version} /server
COPY app.war /server/standalone/deployments/
WORKDIR /server/bin
CMD /server/bin/standalone.bat -b 0.0.0.0
