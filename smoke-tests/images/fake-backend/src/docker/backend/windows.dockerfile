FROM eclipse-temurin:21.0.8_9-jdk-windowsservercore-ltsc2022@sha256:4c08cf81232e0278ca08cb6a267e94c06dbe4f4d705967f7f4bce9c62b4f60c9
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
