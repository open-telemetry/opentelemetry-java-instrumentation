plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.quartz.v2_0")

dependencies {
  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}
