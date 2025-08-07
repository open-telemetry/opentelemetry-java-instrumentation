FROM eclipse-temurin:21-jdk-windowsservercore-ltsc2022
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
