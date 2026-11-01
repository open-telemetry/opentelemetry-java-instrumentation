plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.zaxxer:HikariCP:3.0.0")

  testImplementation(project(":instrumentation:hikaricp-3.0:testing"))
}
