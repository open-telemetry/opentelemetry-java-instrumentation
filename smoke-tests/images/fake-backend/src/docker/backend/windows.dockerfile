FROM eclipse-temurin:21.0.10_7-jdk-windowsservercore-ltsc2022@sha256:0fe161dd961fb138e6b419f8e3166920207daa81225790b05346c930da8c6574
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
