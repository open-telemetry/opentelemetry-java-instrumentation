plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:opensearch:opensearch-java-3.0:javaagent"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("org.opensearch.client:opensearch-java:3.0.0")
}
