FROM eclipse-temurin:11.0.27_6-jdk-windowsservercore-ltsc2022@sha256:55f607ec6508167a25ec418a1768c33a56fd2dc9a435486667a0afbae564d19d
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
