FROM eclipse-temurin:11.0.26_4-jdk-windowsservercore-ltsc2022@sha256:74ae05a1990060243da67eece1c5f1d16a8235464a8b1ff97ea82338eff84f46
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
