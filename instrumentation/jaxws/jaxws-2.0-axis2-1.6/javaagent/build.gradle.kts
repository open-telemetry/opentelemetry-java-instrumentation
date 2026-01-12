plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.axis2")
    module.set("axis2-jaxws")
    versions.set("[1.6.0,)")
    assertInverse.set(true)
    // version 1.2 depends on org.apache.axis2:axis2-kernel:1.2
    // which depends on org.apache.neethi:neethi:2.0.1 which does not exist
    // version 1.3 depends on org.apache.axis2:axis2-kernel:1.3
    // which depends on org.apache.woden:woden:1.0-incubating-M7b which does not exist
    skip("1.2", "1.3")
  }
}

val axis2Version = "1.6.0"

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.axis2:axis2-jaxws:1.6.0")

  compileOnly(project(":muzzle"))
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testLibrary("org.apache.axis2:axis2-jaxws:$axis2Version")
  testLibrary("org.apache.axis2:axis2-transport-http:$axis2Version")
  testLibrary("org.apache.axis2:axis2-transport-local:$axis2Version")

  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0-axis2-1.6:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")

  testImplementation("com.sun.xml.ws:jaxws-rt:2.2.8")
  testImplementation("com.sun.xml.ws:jaxws-tools:2.2.8")

  latestDepTestLibrary("org.apache.axis2:axis2-jaxws:1.+") // see jaxws-3.0-axis2-2.0-testing module
  latestDepTestLibrary("org.apache.axis2:axis2-transport-http:1.+") // see jaxws-3.0-axis2-2.0-testing module
  latestDepTestLibrary("org.apache.axis2:axis2-transport-local:1.+") // see jaxws-3.0-axis2-2.0-testing module
}

configurations.configureEach {
  if (name.contains("test")) {
    // axis has a dependency on servlet2 api, get rid of it - otherwise the servlet3 instrumentation
    // will fail during tests
    exclude("javax.servlet", "servlet-api")
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  systemProperty("metadataConfig", "otel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
