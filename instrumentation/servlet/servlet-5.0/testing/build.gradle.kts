plugins {
  id("otel.javaagent-testing")
}

dependencies {
  api(project(":testing-common"))
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  api(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.0")

  // Tomcat 10.1 requires Java 11
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.+")
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.+")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
  }
}
