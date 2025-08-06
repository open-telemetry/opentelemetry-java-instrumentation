FROM gcr.io/distroless/java-debian10:11

COPY fake-backend.jar /app.jar

EXPOSE 8080
EXPOSE 19000

ENTRYPOINT ["java", "-jar", "/app.jar"] 