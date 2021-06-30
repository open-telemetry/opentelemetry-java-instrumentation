plugins {
  id("otel.shadow-conventions")

  id("otel.java-conventions")
}

dependencies {
  compileOnly("net.bytebuddy:byte-buddy")
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("org.slf4j:slf4j-api")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-api"))
  implementation(project(":javaagent-extension-api"))
  implementation(project(":javaagent-tooling"))
  implementation("io.opentelemetry:opentelemetry-proto")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-metrics")
  implementation("io.grpc:grpc-testing:1.33.1")

  // Include instrumentations instrumenting core JDK classes tp ensure interoperability with other instrumentation
  implementation(project(":instrumentation:executors:javaagent"))
  // FIXME: we should enable this, but currently this fails tests for google http client
  //testImplementation project(":instrumentation:http-url-connection:javaagent")
  implementation(project(":instrumentation:internal:internal-class-loader:javaagent"))
  implementation(project(":instrumentation:internal:internal-eclipse-osgi-3.6:javaagent"))
  implementation(project(":instrumentation:internal:internal-proxy:javaagent"))
  implementation(project(":instrumentation:internal:internal-url-class-loader:javaagent"))

  // Many tests use OpenTelemetry API calls, e.g., via TraceUtils.runUnderTrace
  implementation(project(":instrumentation:opentelemetry-annotations-1.0:javaagent"))
  // TODO (trask) is full OTel API interop needed, or is @WithSpan enough?
  implementation(project(":instrumentation:opentelemetry-api-1.0:javaagent"))
}

tasks {
  jar {
    enabled = false
  }
  shadowJar {
    archiveFileName.set("testing-agent-classloader.jar")
  }
}
