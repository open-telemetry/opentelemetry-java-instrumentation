FROM eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:a8d16dafa7d7976a7c0b723ab1b3d0a27d7024dcb9f4cd543591a5d66a62fecb
COPY fake-backend.jar /fake-backend.jar
CMD ["java", "-jar", "/fake-backend.jar"]
