plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("dev.failsafe:failsafe:3.0.1")

  testImplementation(project(":instrumentation:failsafe-3.0:testing"))
}
