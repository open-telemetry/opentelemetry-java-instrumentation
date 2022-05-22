plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[1.0.0,1.2.3]")
  }
}

dependencies {
  implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))

  library("ch.qos.logback:logback-classic:1.0.0")

  testImplementation(project(":instrumentation:logback:logback-mdc-1.0:testing"))
}
