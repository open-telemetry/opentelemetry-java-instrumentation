plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  api("com.squareup.okhttp3:okhttp:3.0.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
