FROM eclipse-temurin:21.0.13.1_2-jdk-windowsservercore-ltsc2022@sha256:84750d04a9118b3a05c47619a58c5d5b6a95ad14da58db68f613dd7d83609046
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
