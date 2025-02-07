plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:java-http-server:testing"))
}
