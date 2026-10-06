plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.quartz-scheduler")
    module.set("quartz")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:quartz-2.0:library"))

  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.quartz.emit-experimental-telemetry=true")
    systemProperty("metadataConfig", "otel.instrumentation.quartz.emit-experimental-telemetry=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
