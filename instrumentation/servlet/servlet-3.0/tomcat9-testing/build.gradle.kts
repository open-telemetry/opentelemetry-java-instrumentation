plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.0.113")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.0.113")
}
