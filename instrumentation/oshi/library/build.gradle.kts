plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}
