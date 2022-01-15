plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation(project(":testing-common"))
  testImplementation("org.assertj:assertj-core")
}
