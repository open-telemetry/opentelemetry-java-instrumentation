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
  testImplementation(project(":instrumentation:java-http-server:testing"))
}
