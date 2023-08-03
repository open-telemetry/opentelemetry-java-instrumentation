plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.viburdbcp.v11_0")

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}
