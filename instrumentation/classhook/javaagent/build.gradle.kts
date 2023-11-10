plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  testImplementation("org.testcontainers:testcontainers:1.19.1")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}
