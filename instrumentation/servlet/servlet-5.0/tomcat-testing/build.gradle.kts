plugins {
  id("otel.javaagent-testing")
}

dependencies {
  // Servlet instrumentation is tested in Tomcat without Tomcat instrumentation.
  // In Jetty, it is tested with the Jetty app server instrumentation.
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.0")
}

if (otelProps.testLatestDeps) {
  otelJava {
    // Tomcat 10.1 requires Java 11
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks {
  test {
    // exercises an exclude-only selector, which captures every parameter that it does not exclude
    jvmArgs("-Dotel.instrumentation.servlet.experimental.request-parameters.excluded=ignored-*")
  }
}
