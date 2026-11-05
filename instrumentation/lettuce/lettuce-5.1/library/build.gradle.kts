plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.testcontainers-testing")
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }
}
