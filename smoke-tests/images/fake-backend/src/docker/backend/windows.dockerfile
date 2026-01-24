FROM eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:2803c59147fadcab9d75280af8021044e0d48b76bb723688c922e6b58ec41421
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
