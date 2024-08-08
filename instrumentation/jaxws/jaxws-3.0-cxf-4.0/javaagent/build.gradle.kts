plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.cxf")
    module.set("cxf-rt-frontend-jaxws")
    versions.set("[4.0.0,)")
    extraDependency("jakarta.servlet:jakarta.servlet-api:5.0.0")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.cxf:cxf-rt-frontend-jaxws:4.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testLibrary("org.apache.cxf:cxf-rt-transports-http:4.0.0")

  testImplementation(project(":instrumentation:jaxws:jaxws-3.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testImplementation("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
  testImplementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
  testImplementation("org.glassfish.jaxb:jaxb-core:4.0.0")
  testImplementation("org.glassfish.jaxb:jaxb-runtime:4.0.0")
  testImplementation("jakarta.activation:jakarta.activation-api:2.1.0")
  testImplementation("jakarta.annotation:jakarta.annotation-api:2.1.0")
  testImplementation("com.sun.xml.messaging.saaj:saaj-impl:3.0.3")

  latestDepTestLibrary("org.apache.cxf:cxf-rt-frontend-jaxws:4.+")
  latestDepTestLibrary("org.apache.cxf:cxf-rt-transports-http:4.+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
