FROM eclipse-temurin:21.0.8_9-jdk-windowsservercore-ltsc2022@sha256:cb9b993b611e1a3201860d97bcfa92a1a1004bf97ce69685863d50945a7fd547
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
