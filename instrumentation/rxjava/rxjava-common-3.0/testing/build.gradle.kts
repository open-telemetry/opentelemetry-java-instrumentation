plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  api("io.reactivex.rxjava3:rxjava:3.0.0")

  implementation(project(":instrumentation-annotations"))
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("io.opentelemetry:opentelemetry-api")
}
