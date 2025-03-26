FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022@sha256:bb8bda2b4a8a0498b278a2c0c3668e4c17c762f15e7374b0e92df4426640ed35
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
