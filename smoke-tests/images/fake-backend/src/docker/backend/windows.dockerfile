FROM eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:2ef8a2d39eda78a0c9074e1b2c206433da70f80331c6e084f47520f4cebdc4e7
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
