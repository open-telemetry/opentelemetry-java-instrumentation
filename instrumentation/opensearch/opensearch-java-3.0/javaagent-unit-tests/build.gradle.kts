plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(
    project(":instrumentation:opensearch:opensearch-java-3.0:javaagent"),
  )
  testImplementation("org.opensearch.client:opensearch-java:3.0.0")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
}
