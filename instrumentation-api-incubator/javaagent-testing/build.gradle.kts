plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))

  testImplementation(project(":testing-common"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.http.client.emit-experimental-telemetry=true")
  jvmArgs("-Dotel.instrumentation.http.client.url-template-rules=http://localhost:.*/hello/.*,/hello/*")
}
