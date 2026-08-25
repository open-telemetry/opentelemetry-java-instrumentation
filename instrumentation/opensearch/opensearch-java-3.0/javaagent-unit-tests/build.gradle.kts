plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:javaagent"))
  testImplementation(project(":instrumentation:opensearch:opensearch-java-3.0:javaagent"))
}
