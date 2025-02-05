FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
