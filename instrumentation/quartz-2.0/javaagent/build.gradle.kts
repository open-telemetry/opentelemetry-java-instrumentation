plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.quartz-scheduler")
    module.set("quartz")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
    skip("1.7.0") // missing in maven central
  }
}

dependencies {
  implementation(project(":instrumentation:quartz-2.0:library"))

  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.quartz.experimental-span-attributes=true")
}
