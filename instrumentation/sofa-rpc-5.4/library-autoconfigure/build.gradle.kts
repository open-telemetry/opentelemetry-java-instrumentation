plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // 5.4.0 is the recommended minimum version for production use
  library("com.alipay.sofa:sofa-rpc-all:5.4.0")

  testImplementation(project(":instrumentation:sofa-rpc-5.4:testing"))
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc")
  }

  val testBothSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
