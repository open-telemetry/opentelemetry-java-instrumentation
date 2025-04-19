plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.avaje")
    module.set("avaje-jex")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

dependencies {
  library("io.avaje:avaje-jex:3.0")
  testLibrary("org.eclipse.jetty:jetty-http-spi:12.0.19")
  testInstrumentation(project(":instrumentation:jetty:jetty-12.0:javaagent"))
}
