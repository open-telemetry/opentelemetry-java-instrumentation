FROM eclipse-temurin:21.0.12_8-jdk-windowsservercore-ltsc2022@sha256:87883996bf34a15b691000be1f87650e523d995e8be6871c0bf98c601950f65a
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
