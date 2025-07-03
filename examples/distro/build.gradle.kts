plugins {
  id("otel.instrumentation-conventions")
}

group = "io.opentelemetry.example"
version = "1.0-SNAPSHOT"

subprojects {
  version = rootProject.version
} 