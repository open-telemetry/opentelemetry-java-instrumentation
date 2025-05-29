plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  // Resteasy changes a class's package in 3.1.0 then moves it back in 3.5.0 and then moves it forward again in 4.0.0
  // so the jaxrs-2.0-resteasy-3.0 module applies to [3.0, 3.1) and [3.5, 4.0)
  // and the jaxrs-2.0-resteasy-3.1 module applies to [3.1, 3.5) and [4.0, )
  pass {
    group.set("org.jboss.resteasy")
    module.set("resteasy-jaxrs")
    versions.set("[3.0.0.Final,3.1.0.Final)")
  }

  pass {
    group.set("org.jboss.resteasy")
    module.set("resteasy-jaxrs")
    versions.set("[3.5.0.Final,4)")
  }

  fail {
    group.set("org.jboss.resteasy")
    module.set("resteasy-jaxrs")
    versions.set("(2.1.0.GA,3.0.0.Final)")
    // missing dependencies
    skip("2.3.10.Final")
  }

  fail {
    group.set("org.jboss.resteasy")
    module.set("resteasy-core")
    versions.set("[4.0.0.Final,)")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
  library("org.jboss.resteasy:resteasy-jaxrs:3.0.0.Final")

  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))
  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:undertow-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-cxf-3.2:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-jersey-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-jersey-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-resteasy-6.0:javaagent"))

  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:testing"))
  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.6.v20170531")

  testLibrary("org.jboss.resteasy:resteasy-undertow:3.0.4.Final") {
    exclude("org.jboss.resteasy", "resteasy-client")
  }
  testImplementation("io.undertow:undertow-servlet:1.4.28.Final")
  testLibrary("org.jboss.resteasy:resteasy-servlet-initializer:3.0.4.Final")

  latestDepTestLibrary("org.jboss.resteasy:resteasy-servlet-initializer:3.0.+") // see jaxrs-3.0-resteasy-3.1 module
  latestDepTestLibrary("org.jboss.resteasy:resteasy-jaxrs:3.0.+") // see jaxrs-3.0-resteasy-3.1 module
  latestDepTestLibrary("org.jboss.resteasy:resteasy-undertow:3.0.+") { // see jaxrs-3.0-resteasy-3.1 module
    exclude("org.jboss.resteasy", "resteasy-client")
  }
  latestDepTestLibrary("io.undertow:undertow-servlet:2.2.24.Final") // see jaxrs-3.0-resteasy-3.1 module
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.jaxrs.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
