plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.helidon.webserver")
    module.set("helidon-webserver")
    versions.set("[4.3.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

dependencies {
  library("io.helidon.webserver:helidon-webserver:4.3.0")
  library(project(":instrumentation:helidon:helidon-4.3.0:library"))
  testImplementation(project(":instrumentation:helidon:helidon-4.3.0:testing"))
}
