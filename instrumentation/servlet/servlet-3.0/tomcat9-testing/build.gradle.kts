plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing"))
  testImplementation(project(":instrumentation:servlet:servlet-common:bootstrap"))
  testRuntimeOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testImplementation("org.apache.tomcat.embed:tomcat-embed-core") {
    version {
      strictly("9.0.113")
    }
  }
  testImplementation("org.apache.tomcat.embed:tomcat-embed-jasper") {
    version {
      strictly("9.0.113")
    }
  }
}
