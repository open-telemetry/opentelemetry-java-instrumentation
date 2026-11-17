FROM eclipse-temurin:21.0.13_9-jdk-windowsservercore-ltsc2022@sha256:bc045a231c63f5600bba2f45482e13b7a8e13202670cf08dfe8c9f261916fa58
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
