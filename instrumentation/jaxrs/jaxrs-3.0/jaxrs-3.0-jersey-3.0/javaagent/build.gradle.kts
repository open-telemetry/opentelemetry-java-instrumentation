plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.jersey.core")
    module.set("jersey-server")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
    extraDependency("jakarta.servlet:jakarta.servlet-api:5.0.0")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  library("org.glassfish.jersey.core:jersey-server:3.0.0")
  library("org.glassfish.jersey.containers:jersey-container-servlet:3.0.0")
  library("org.glassfish.jersey.inject:jersey-hk2:3.0.0")
  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))

  testImplementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:testing"))
  testImplementation("org.eclipse.jetty:jetty-webapp:11.0.0")
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.jaxrs.experimental-span-attributes=true")
  }
}
