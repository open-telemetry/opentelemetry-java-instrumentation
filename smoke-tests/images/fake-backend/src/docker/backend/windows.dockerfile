FROM eclipse-temurin:21.0.12_8-jdk-windowsservercore-ltsc2022@sha256:858958399710bd20a18ded95d68525c68a1bde1899284369d04a83b916093a15
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
