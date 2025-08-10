plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))

  latestDepTestLibrary("com.openai:openai-java:2.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
