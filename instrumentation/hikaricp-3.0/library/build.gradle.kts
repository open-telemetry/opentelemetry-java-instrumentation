plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.hikaricp.v3_0")

dependencies {
  library("com.zaxxer:HikariCP:3.0.0")

  testImplementation(project(":instrumentation:hikaricp-3.0:testing"))
}
