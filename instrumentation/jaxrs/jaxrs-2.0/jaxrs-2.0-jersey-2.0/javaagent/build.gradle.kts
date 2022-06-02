plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.jersey.core")
    module.set("jersey-server")
    versions.set("[2.0,3.0.0)")
    extraDependency("javax.servlet:javax.servlet-api:3.1.0")
    assertInverse.set(true)
  }
  pass {
    group.set("org.glassfish.jersey.containers")
    module.set("jersey-container-servlet")
    versions.set("[2.0,3.0.0)")
    extraDependency("javax.servlet:javax.servlet-api:3.1.0")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
  library("org.glassfish.jersey.core:jersey-server:2.0")
  library("org.glassfish.jersey.containers:jersey-container-servlet:2.0")

  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:testing"))
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.6.v20170531")

  latestDepTestLibrary("org.glassfish.jersey.core:jersey-server:2.+")
  latestDepTestLibrary("org.glassfish.jersey.containers:jersey-container-servlet:2.+")
  latestDepTestLibrary("org.glassfish.jersey.containers:jersey-container-servlet:2.+")
  latestDepTestLibrary("org.glassfish.jersey.inject:jersey-hk2:2.+")
}

if (!(findProperty("testLatestDeps") as Boolean)) {
  // early jersey versions require old guava
  configurations.testRuntimeClasspath.resolutionStrategy.force("com.google.guava:guava:14.0.1")

  configurations {
    // early jersey versions bundle asm without shading
    testImplementation {
      exclude("org.ow2.asm", "asm")
      exclude("org.ow2.asm", "asm-commons")
    }
  }
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.jaxrs.experimental-span-attributes=true")
  }
}
