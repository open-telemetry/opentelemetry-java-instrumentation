FROM eclipse-temurin:21.0.8_9-jdk-windowsservercore-ltsc2022@sha256:b5f3c18f0235658f400cb75ab69c650eb3f8c03aa12135759dffdf7f67836dc5
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
