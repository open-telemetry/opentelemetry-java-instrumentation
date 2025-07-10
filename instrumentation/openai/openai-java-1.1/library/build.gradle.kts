plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))

  // needed for latest dep tests
  testCompileOnly("com.google.errorprone:error_prone_annotations")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
