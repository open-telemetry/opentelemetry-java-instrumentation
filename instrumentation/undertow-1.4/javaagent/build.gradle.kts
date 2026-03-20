plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.undertow")
    module.set("undertow-core")
    versions.set("[1.4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.undertow:undertow-core:2.0.0.Final")

  bootstrap(project(":instrumentation:executors:bootstrap"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))
  bootstrap(project(":instrumentation:undertow-1.4:bootstrap"))
}

tasks.withType<Test>().configureEach {
  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
}

// since 2.3.x, undertow is compiled by JDK 11
val latestDepTest = findProperty("testLatestDeps") as Boolean
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
