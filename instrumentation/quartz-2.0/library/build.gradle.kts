plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}
