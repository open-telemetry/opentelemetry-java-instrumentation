plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}
