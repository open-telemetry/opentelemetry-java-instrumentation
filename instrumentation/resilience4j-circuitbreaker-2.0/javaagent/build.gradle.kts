plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.github.resilience4j")
    module.set("resilience4j-circuitbreaker")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  library("io.github.resilience4j:resilience4j-circuitbreaker:2.0.0")
  latestDepTestLibrary("io.github.resilience4j:resilience4j-circuitbreaker:latest.release")
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.resilience4j-circuitbreaker.enabled=true")
  }

  val testDisabled = register<Test>("testDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("*DefaultEnablementTest")
    }
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.resilience4j-circuitbreaker.enabled=true",
      "-Dotel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
  }

  check {
    dependsOn(testDisabled, testExperimental)
  }
}
