plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.apachedbcp.v2_0")

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
}
