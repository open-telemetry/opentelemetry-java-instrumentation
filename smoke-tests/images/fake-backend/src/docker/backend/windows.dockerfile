FROM eclipse-temurin:21.0.11_10-jdk-windowsservercore-ltsc2022@sha256:4298c2f09584dea72e4f8269c326ba6fb42af6603eb68b65f0d34e2f0b8bf1e8
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
