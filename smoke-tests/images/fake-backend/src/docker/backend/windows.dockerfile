FROM eclipse-temurin:11.0.27_6-jdk-windowsservercore-ltsc2022@sha256:dce5dfb7884802f4c29368860d1b7b4ca291f6525be2d4df80cbd2cd5cb1f9d9
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
