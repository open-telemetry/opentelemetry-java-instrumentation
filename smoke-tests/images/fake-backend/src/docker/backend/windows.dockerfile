FROM eclipse-temurin:21.0.10_7-jdk-windowsservercore-ltsc2022@sha256:9c6fac7352da02f3b1620dc7cc0b69b49dc93f72d2856a4d98856864c3329833
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
