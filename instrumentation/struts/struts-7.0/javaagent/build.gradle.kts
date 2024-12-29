plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.struts")
    module.set("struts2-core")
    versions.set("[7.0.0,)")
    assertInverse.set(true)
  }
}

// struts 7 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.struts:struts2-core:7.0.0")

  testImplementation(project(":testing-common"))
  testImplementation("org.eclipse.jetty:jetty-server:11.0.0")
  testImplementation("org.eclipse.jetty:jetty-servlet:11.0.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  testImplementation("jakarta.servlet.jsp:jakarta.servlet.jsp-api:3.0.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testInstrumentation(project(":instrumentation:struts:struts-2.3:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
