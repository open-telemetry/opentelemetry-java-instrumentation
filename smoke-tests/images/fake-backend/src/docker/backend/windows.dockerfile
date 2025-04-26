FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022@sha256:0b4517b4df4900540bafcf7ec7184c140c1d2a6b519aebbd4d63825587ddd08c
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
