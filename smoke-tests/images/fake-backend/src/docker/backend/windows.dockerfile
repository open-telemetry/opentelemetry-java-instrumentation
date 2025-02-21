FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022@sha256:23863560d1c34098a0e4e3effb1bdeab73639c404183441e112067c782b4a351
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
