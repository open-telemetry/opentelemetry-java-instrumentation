plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("com.zaxxer:HikariCP:3.0.0")
}
