FROM eclipse-temurin:11.0.27_6-jdk-windowsservercore-ltsc2022@sha256:5469ceab8d1f5cc620818109fb2e7e2b56bbe010257240348011e16557f9e2b7
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
