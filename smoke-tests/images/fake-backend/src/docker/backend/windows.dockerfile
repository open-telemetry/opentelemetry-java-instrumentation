FROM eclipse-temurin:21.0.11_10-jdk-windowsservercore-ltsc2022@sha256:623688f10feff4036496c832c37a789ee49bb5486a628d0938a377d97be40012
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
