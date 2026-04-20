FROM eclipse-temurin:21.0.10_7-jdk-windowsservercore-ltsc2022@sha256:f67fb0a74dfd10d5a54d31b232ed74e3941fcc40345de5e03378693f3f5f5d3e
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
