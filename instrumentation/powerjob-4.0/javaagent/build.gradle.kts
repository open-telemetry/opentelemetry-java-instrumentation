plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("tech.powerjob")
    module.set("powerjob-worker")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
    extraDependency("tech.powerjob:powerjob-official-processors:1.1.0")
  }
}

dependencies {
  library("tech.powerjob:powerjob-worker:4.0.0")
  library("tech.powerjob:powerjob-official-processors:1.1.0")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.powerjob.experimental-span-attributes=true")
}
