plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.linecorp.armeria:armeria:1.3.0")

  testImplementation(project(":instrumentation:armeria-1.3:testing"))
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
