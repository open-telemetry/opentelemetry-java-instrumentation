plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testLibrary("com.sun.xml.ws:jaxws-rt:2.2.0.1")
  // early versions of streambuffer depend on latest release of org.jvnet.staxex:stax-ex
  // which doesn't work with java 8
  testLibrary("com.sun.xml.stream.buffer:streambuffer:1.4")

  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-metro-2.2:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  latestDepTestLibrary("com.sun.xml.ws:jaxws-rt:2.+") // see jaxws-3.0-metro-2.2-testing module
  latestDepTestLibrary("com.sun.xml.stream.buffer:streambuffer:1.+") // see jaxws-3.0-metro-2.2-testing module
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-exports=java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED")
  jvmArgs("--add-exports=java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
