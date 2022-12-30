plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testLibrary("io.opentelemetry:opentelemetry-opencensus-shim")
  testCompileOnly("io.opentelemetry:opentelemetry-api")
}
