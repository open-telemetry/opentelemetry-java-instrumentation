FROM eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:45a3d356d018942a497b877633f19db401828ecb2a1de3cda635b98d08bfbaeb
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
