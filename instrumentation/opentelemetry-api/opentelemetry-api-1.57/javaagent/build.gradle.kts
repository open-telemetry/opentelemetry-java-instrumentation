plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_57"))
}

tasks.withType<Test>().configureEach {
  jvmArgs(
    "-Dotel.experimental.config.file=$projectDir/src/test/resources/declarative-config.yaml"
  )
}
