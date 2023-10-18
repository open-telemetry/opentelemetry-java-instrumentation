plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
  testImplementation("com.sun.xml.ws:jaxws-rt:2.3.6")

  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-metro-2.2:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  latestDepTestLibrary("com.sun.xml.ws:jaxws-rt:2.+")
  latestDepTestLibrary("com.sun.xml.stream.buffer:streambuffer:1.+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-exports=java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED")
  jvmArgs("--add-exports=java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
