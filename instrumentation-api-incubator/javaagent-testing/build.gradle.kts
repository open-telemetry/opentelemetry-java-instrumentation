plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))

  testImplementation(project(":testing-common"))
}

tasks {
  test {
    jvmArgs(
      "-Dotel.experimental.config.file=$projectDir/src/test/resources/declarative-config.yaml"
    )
  }
}
