plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    // required on jdk17
    jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    // with default settings tests will fail when disk is 90% full
    jvmArgs("-Drocketmq.broker.diskSpaceWarningLevelRatio=1.0")

    // used for experimental attributes test assertion logic which looks for this property
    jvmArgs("-Dotel.instrumentation.rocketmq-client.experimental-span-attributes=true")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
  }

  check {
    dependsOn(testV3Preview, testBothSemconv)
  }
}
