plugins {
  id("otel.javaagent-testing")
}

dependencies {
  val axis2Version = "2.0.0"
  testLibrary("org.apache.axis2:axis2-jaxws:$axis2Version") {
    exclude(group = "org.eclipse.jetty.ee9")
  }
  testLibrary("org.apache.axis2:axis2-transport-http:$axis2Version")
  testLibrary("org.apache.axis2:axis2-transport-local:$axis2Version")

  testImplementation(project(":instrumentation:jaxws:jaxws-3.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0-axis2-1.6:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
