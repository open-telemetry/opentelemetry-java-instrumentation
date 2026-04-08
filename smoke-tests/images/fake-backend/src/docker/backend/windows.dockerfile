FROM eclipse-temurin:21.0.10_7-jdk-windowsservercore-ltsc2022@sha256:fc3ff870d7aef09224903913ad3dcb2606ba240f59b102297abccde678fffb13
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
