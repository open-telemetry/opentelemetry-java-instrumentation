FROM eclipse-temurin:21.0.11_10-jdk-windowsservercore-ltsc2022@sha256:66c7f7a70501ba18858830fd81b19a6668420251a5b7c3fa8d95125a448b8d27
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
