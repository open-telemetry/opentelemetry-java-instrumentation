FROM eclipse-temurin:21.0.11_10-jdk-windowsservercore-ltsc2022@sha256:7302d592e1aaa383bd83dd430643a64a1a16bf68a817dc7eb795e6cf28f73834
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
