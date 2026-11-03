FROM eclipse-temurin:21.0.13_9-jdk-windowsservercore-ltsc2022@sha256:c5e2be747d154e1949731f9429ae0285217ae3bdded8d82fbea1c4da4adb2aad
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
