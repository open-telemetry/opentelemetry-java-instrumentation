plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.ws.rs")
    module.set("jsr311-api")
    versions.set("[0.5,)")
  }
  fail {
    group.set("javax.ws.rs")
    module.set("javax.ws.rs-api")
    versions.set("[,]")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:jsr311-api:1.1.1")

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.6.v20170531")
  testImplementation("com.sun.jersey:jersey-servlet:1.19.4")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:activation:1.1.1")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
