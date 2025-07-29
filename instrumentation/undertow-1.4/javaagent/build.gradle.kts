plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.undertow")
    module.set("undertow-core")
    versions.set("[1.4.0.Final,)")
    assertInverse.set(true)
    // release missing in maven central
    skip("2.2.25.Final")
  }
}

dependencies {
  library("io.undertow:undertow-core:2.0.0.Final")

  bootstrap(project(":instrumentation:executors:bootstrap"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))
  bootstrap(project(":instrumentation:undertow-1.4:bootstrap"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}

// since 2.3.x, undertow is compiled by JDK 11
val latestDepTest = findProperty("testLatestDeps") as Boolean
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
