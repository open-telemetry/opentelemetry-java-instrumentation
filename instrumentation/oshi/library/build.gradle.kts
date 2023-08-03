plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.oshi")

dependencies {
  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}
