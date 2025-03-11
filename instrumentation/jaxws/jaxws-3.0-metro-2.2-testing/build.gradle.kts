plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testLibrary("com.sun.xml.ws:jaxws-rt:3.0.0")

  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  testImplementation(project(":instrumentation:jaxws:jaxws-3.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-metro-2.2:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
