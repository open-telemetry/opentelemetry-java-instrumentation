plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alipay.sofa")
    module.set("sofa-rpc-all")
    versions.set("[5.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:sofa-rpc-5.4:library"))

  library("com.alipay.sofa:sofa-rpc-all:5.4.0")
}

testing {
  suites {
    register<JvmTestSuite>("testSofaRpc") {
      dependencies {
        implementation(project(":instrumentation:sofa-rpc-5.4:testing"))
        implementation("com.alipay.sofa:sofa-rpc-all:${baseVersion("5.4.0").orLatest()}")
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

  systemProperty("collectMetadata", otelProps.collectMetadata)
}

val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}StableSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc,service.peer")
  }
}

val bothSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}BothSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup,service.peer")
  }
}

tasks {
  check {
    dependsOn(testing.suites, stableSemconvSuites, bothSemconvSuites)
  }
}

configurations.named("testSofaRpcRuntimeClasspath") {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}

if (otelProps.denyUnsafe) {
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
