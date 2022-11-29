FROM eclipse-temurin:11-jdk-windowsservercore-ltsc2022
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
