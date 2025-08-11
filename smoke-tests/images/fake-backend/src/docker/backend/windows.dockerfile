FROM eclipse-temurin:21.0.8_9-jdk-windowsservercore-ltsc2022@sha256:87e4af970a21c3a1eb37b39c42621308f71e16cf95bbfbc8e66ad77d6582b1a3
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
