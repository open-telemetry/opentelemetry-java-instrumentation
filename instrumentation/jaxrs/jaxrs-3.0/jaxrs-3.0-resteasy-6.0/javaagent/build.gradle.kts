plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jboss.resteasy")
    module.set("resteasy-core")
    versions.set("[6.0.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
  library("org.jboss.resteasy:resteasy-core:6.0.0.Final")

  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))

  testImplementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:testing"))
  testImplementation("org.eclipse.jetty:jetty-webapp:11.0.0")
  testLibrary("org.jboss.resteasy:resteasy-undertow:6.0.0.Final") {
    exclude("org.jboss.resteasy", "resteasy-client")
  }
  testLibrary("io.undertow:undertow-servlet-jakarta:2.2.17.Final")
  testLibrary("org.jboss.resteasy:resteasy-servlet-initializer:6.0.0.Final")
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.jaxrs.experimental-span-attributes=true")
  }
}
