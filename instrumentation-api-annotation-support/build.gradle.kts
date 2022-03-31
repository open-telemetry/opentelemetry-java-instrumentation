plugins {
  id("otel.java-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  implementation(project(":instrumentation-api"))

  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-semconv")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
