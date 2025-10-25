plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.netflix.hystrix")
    module.set("hystrix-core")
    versions.set("[1.4.0,)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.netflix.hystrix:hystrix-core:1.4.0")
  library("io.reactivex:rxjava:1.0.8")
}

tasks {
  withType<Test>().configureEach {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    jvmArgs("-Dhystrix.command.default.circuitBreaker.enabled=false")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")

    // Uncomment for debugging:
    // jvmArgs("-Dhystrix.command.default.execution.timeout.enabled=false")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.hystrix.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.hystrix.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
