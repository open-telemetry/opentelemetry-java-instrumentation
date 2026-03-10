plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.apache.iceberg:iceberg-core:1.8.1")
  testImplementation(project(":instrumentation:iceberg-1.8:testing"))
  implementation("io.opentelemetry:opentelemetry-api-incubator")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

tasks.test {
  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
}
