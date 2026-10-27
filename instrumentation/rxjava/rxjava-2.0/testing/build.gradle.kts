plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")
  api("io.reactivex.rxjava2:rxjava:2.0.6")

  implementation("io.opentelemetry:opentelemetry-api")
}
