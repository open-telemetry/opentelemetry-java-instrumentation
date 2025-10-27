FROM eclipse-temurin:21.0.8_9-jdk-windowsservercore-ltsc2022@sha256:47afec211682c034b7188eb1d01210de503bcb3bb8e47f7b60198ef825cdca4b
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
