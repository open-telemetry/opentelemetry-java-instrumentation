plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.linecorp.armeria:armeria:1.3.0")

  testImplementation(project(":instrumentation:armeria:armeria-1.3:testing"))

  // needed for latest dep tests
  testCompileOnly("com.google.errorprone:error_prone_annotations")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
