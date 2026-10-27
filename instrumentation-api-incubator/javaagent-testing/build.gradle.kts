plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))

  testImplementation(project(":instrumentation-testing"))
}

tasks {
  test {
    jvmArgs(
      "-Dotel.config.file=$projectDir/src/test/resources/declarative-config.yaml"
    )
  }
}
