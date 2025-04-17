FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022@sha256:882394b8817e22f505cc97857e34a30f38d0198618d8325c072d5830589ab22b
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
