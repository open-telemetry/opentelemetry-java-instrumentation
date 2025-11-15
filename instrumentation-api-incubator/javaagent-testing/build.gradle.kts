plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))

  testImplementation(project(":testing-common"))
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.http.client.emit-experimental-telemetry=true")
    jvmArgs("-Dotel.instrumentation.http.client.url-template-rules=http://localhost:.*/hello/.*,/hello/*")
  }

  val declarativeConfigTest by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.experimental.config.file=$projectDir/src/test/resources/declarative-config.yaml"
    )
  }

  check {
    dependsOn(declarativeConfigTest)
  }
}
