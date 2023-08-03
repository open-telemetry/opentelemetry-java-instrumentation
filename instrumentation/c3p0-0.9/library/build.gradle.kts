plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.c3p0.v0_9")

dependencies {
  library("com.mchange:c3p0:0.9.2")

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
}
