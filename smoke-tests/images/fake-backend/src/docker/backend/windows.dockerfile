FROM eclipse-temurin:11.0.27_6-jdk-windowsservercore-ltsc2022@sha256:48e3b80f8ff69d03f5c9eef0769eaf01f7874fe799cb48a4780e014843b3e484
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
