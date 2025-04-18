plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("org.apache.thrift:libthrift:0.14.0")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")

  implementation(project(":instrumentation:thrift:thrift-0.9.1:javaagent")) {
    exclude("org.apache.thrift", "libthrift")
  }
}
